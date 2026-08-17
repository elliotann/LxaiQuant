/**
 * K线数据管理 Composable
 * 提供K线数据的加载、更新和管理功能
 */
import { ref, Ref } from "vue";
import { DataManager } from "@/utils/kline/DataManager";
import { getklineData, getUnshiftData, getLatestKlineData } from "@/api/member";

export function useKlineData(state: any, chart: Ref<any>) {
  const isLoading = ref(false);
  const error = ref<string | null>(null);

  /**
   * 加载K线数据
   */
  const loadKlineData = async () => {
    if (!state.dataManager) {
      console.warn("DataManager 未初始化");
      return;
    }

    isLoading.value = true;
    error.value = null;

    try {
      const data = await state.dataManager.loadInitialData();
      if (data && data.length > 0) {
        state.chartData = data;
        updateChart();
      }
    } catch (err: any) {
      error.value = err.message || "加载K线数据失败";
      console.error("加载K线数据失败:", err);
    } finally {
      isLoading.value = false;
    }
  };

  /**
   * 更新最新K线数据
   */
  const updateLatestKLine = async () => {
    if (!state.dataManager) return;

    try {
      const latestData = await state.dataManager.getLatestData();
      if (latestData) {
        // 更新图表数据
        if (chart.value) {
          chart.value.updateLatestBar(latestData);
        }
        // 更新状态
        state.latestKlineData = latestData;
        state.currentPrice = latestData.close;
        updatePriceChange();
      }
    } catch (err) {
      console.error("更新最新K线数据失败:", err);
    }
  };

  /**
   * 获取最新K线数据
   */
  const getLatestKLineData = async () => {
    return updateLatestKLine();
  };

  /**
   * 如果需要，加载更多K线数据
   */
  const loadMoreKlineDataIfNeeded = async (timeRange: {
    from: number;
    to: number;
  }) => {
    if (!state.dataManager) return;

    try {
      const data = await state.dataManager.loadMoreDataIfNeeded(timeRange);
      if (data && data.length > 0) {
        state.chartData = [...state.chartData, ...data];
        updateChart();
      }
    } catch (err) {
      console.error("加载更多K线数据失败:", err);
    }
  };

  /**
   * 合并K线数据
   */
  const mergeKlineData = (newData: any[]) => {
    if (!newData || newData.length === 0) return;

    // 确保新数据也按时间排序
    const sortedNewData = [...newData].sort(
      (a: any, b: any) => a.time - b.time,
    );

    // 合并逻辑：使用 Map 去重，确保时间唯一
    const dataMap = new Map();

    // 将现有数据添加到 Map
    if (state.chartData && state.chartData.length > 0) {
      state.chartData.forEach((item: any) => {
        if (item && item.time != null) {
          dataMap.set(item.time, item);
        }
      });
    }

    // 将新数据添加到 Map（自动去重）
    sortedNewData.forEach((item: any) => {
      if (item && item.time != null) {
        dataMap.set(item.time, item);
      }
    });

    // 转换为数组并按时间排序
    const mergedData = Array.from(dataMap.values()).sort(
      (a: any, b: any) => a.time - b.time,
    );

    // 验证排序
    let isSorted = true;
    for (let i = 1; i < mergedData.length; i++) {
      if (mergedData[i].time < mergedData[i - 1].time) {
        isSorted = false;
        console.warn(
          `⚠️ [mergeKlineData] 数据排序验证失败，索引 ${i}: time=${mergedData[i].time}, prev time=${mergedData[i - 1].time}`,
        );
        break;
      }
    }

    if (!isSorted) {
      console.warn("⚠️ [mergeKlineData] 数据未正确排序，重新排序...");
      mergedData.sort((a: any, b: any) => a.time - b.time);
    }

    if (mergedData.length > 0) {
      state.chartData = mergedData;
      updateChart();
    }
  };

  /**
   * 防抖更新图表
   */
  let debounceTimer: NodeJS.Timeout | null = null;
  const debouncedUpdateChart = () => {
    if (debounceTimer) {
      clearTimeout(debounceTimer);
    }
    debounceTimer = setTimeout(() => {
      updateChart();
    }, 100);
  };

  /**
   * 更新图表
   */
  const updateChart = () => {
    if (chart.value && state.chartData) {
      chart.value.updateData(state.chartData);
    }
  };

  /**
   * 获取时间间隔（秒）
   */
  const getIntervalSeconds = (interval: string): number => {
    const intervalMap: Record<string, number> = {
      OKXMIN1: 60,
      OKXMIN3: 180,
      OKXMIN5: 300,
      OKXMIN15: 900,
      OKXMIN30: 1800,
      OKXMIN60: 3600,
      OKX4HOUR: 14400,
      OKX1D: 86400,
    };
    return intervalMap[interval] || 180;
  };

  /**
   * 更新价格变化百分比
   */
  const updatePriceChange = () => {
    if (state.basePrice && state.currentPrice) {
      const change =
        ((state.currentPrice - state.basePrice) / state.basePrice) * 100;
      state.priceChangePercent = change.toFixed(2);
    }
  };

  return {
    isLoading,
    error,
    loadKlineData,
    updateLatestKLine,
    getLatestKLineData,
    loadMoreKlineDataIfNeeded,
    mergeKlineData,
    debouncedUpdateChart,
    getIntervalSeconds,
  };
}
