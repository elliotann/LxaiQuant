import { ref, reactive, onMounted, onUnmounted, watch, nextTick } from "vue";
import { createChart } from "lightweight-charts";
import { useChartIndicators } from "./useChartIndicators";
import { useDataManager } from "./useDataManager";
import { PerformanceOptimizer } from "./PerformanceOptimizer";

export function useLightweightChart(options = {}) {
  const chartContainer = ref(null);
  const chart = ref(null);
  const candlestickSeries = ref(null);

  const config = {
    width: 800,
    height: 400,
    layout: {
      background: { color: "#ffffff" },
      textColor: "#333333",
    },
    grid: {
      vertLines: { color: "#e1e1e1" },
      horzLines: { color: "#e1e1e1" },
    },
    ...options,
  };

  // 使用组合式函数
  const dataManager = useDataManager({
    throttleTime: 1500,
    maxDataPoints: 1000,
  });

  const indicatorsManager = useChartIndicators(chart);

  // 事件订阅管理
  const subscriptions = reactive({
    crosshair: null,
    timeScale: null,
    click: null,
  });

  // 性能监控
  const performanceMonitor = {
    renderThrottle: 16,
    lastRenderTime: 0,
    isDragging: false,
  };

  // 初始化图表
  function initChart() {
    if (!chartContainer.value) return;

    chart.value = createChart(chartContainer.value, {
      width: config.width,
      height: config.height,
      layout: config.layout,
      grid: config.grid,
      timeScale: {
        timeVisible: true,
        secondsVisible: false,
      },
      rightPriceScale: {
        visible: true,
      },
    });

    // 创建K线系列
    candlestickSeries.value = chart.value.addCandlestickSeries({
      upColor: "#26a69a",
      downColor: "#ef5350",
      borderVisible: false,
      wickUpColor: "#26a69a",
      wickDownColor: "#ef5350",
    });

    // 设置事件监听
    setupEventListeners();

    // 注册数据更新回调
    dataManager.addUpdateCallback((data, preserveView) => {
      updateChartData(data, preserveView);
    });

    console.log("LightweightChart initialized with composables");
  }

  // 设置事件监听
  function setupEventListeners() {
    if (!chart.value) return;

    // 十字线移动事件
    subscriptions.crosshair = chart.value.subscribeCrosshairMove((param) => {
      // 处理十字线移动逻辑
      handleCrosshairMove(param);
    });

    // 时间刻度变化事件（用于指标重算）
    subscriptions.timeScale = chart.value
      .timeScale()
      .subscribeVisibleTimeRangeChange((timeRange) => {
        if (performanceMonitor.isDragging) return;

        // 使用节流控制更新频率
        const now = Date.now();
        if (
          now - performanceMonitor.lastRenderTime <
          performanceMonitor.renderThrottle
        ) {
          return;
        }
        performanceMonitor.lastRenderTime = now;

        requestAnimationFrame(() => {
          recalculateAllIndicators();
        });
      });

    // 点击事件
    subscriptions.click = chart.value.subscribeClick((param) => {
      handleChartClick(param);
    });
  }

  // 更新图表数据
  function updateChartData(data, preserveView = false) {
    if (!candlestickSeries.value || !data) return;

    try {
      if (preserveView) {
        // 保持当前视图范围
        const currentTimeRange = chart.value.timeScale().getVisibleRange();
        candlestickSeries.value.setData(data);

        if (currentTimeRange) {
          chart.value.timeScale().setVisibleRange(currentTimeRange);
        }
      } else {
        candlestickSeries.value.setData(data);
      }

      // 重新计算所有指标
      nextTick(() => {
        recalculateAllIndicators();
      });
    } catch (error) {
      console.error("更新图表数据失败:", error);
    }
  }

  // 重新计算所有指标
  function recalculateAllIndicators() {
    if (!candlestickSeries.value) return;

    const allData = candlestickSeries.value.data();
    if (!allData || allData.length === 0) return;

    console.log("🔄 使用组合式函数重新计算所有指标，数据量:", allData.length);

    // 通过indicatorsManager批量更新
    const indicatorConfigs = getIndicatorConfigs();
    indicatorsManager.updateAllIndicators(indicatorConfigs, allData);
  }

  // 获取指标配置（需要从props或外部传入）
  function getIndicatorConfigs() {
    // 这里需要根据实际的props来获取配置
    // 暂时返回空对象，实际使用时需要传入
    return {};
  }

  // 初始化指标
  function initIndicators(indicatorConfigs) {
    indicatorsManager.initIndicators(indicatorConfigs);
  }

  // 处理十字线移动
  function handleCrosshairMove(param) {
    // 处理十字线移动逻辑
    // 可以在这里更新tooltip或其他UI元素
  }

  // 处理图表点击
  function handleChartClick(param) {
    // 处理图表点击逻辑
    // 可以在这里处理标记、选择等功能
  }

  // 调整图表大小
  function resize(width, height) {
    if (chart.value) {
      chart.value.applyOptions({ width, height });
    }
  }

  // 添加标记
  function addMarker(marker) {
    if (candlestickSeries.value) {
      candlestickSeries.value.setMarkers([marker]);
    }
  }

  // 添加线系列
  function addLineSeries(data, options = {}) {
    if (!chart.value) return null;

    const lineSeries = chart.value.addLineSeries({
      color: "#2196F3",
      lineWidth: 2,
      ...options,
    });

    const formattedData = data.map((item) => ({
      time: item.time,
      value: item.value,
    }));

    lineSeries.setData(formattedData);
    return lineSeries;
  }

  // 清理资源
  function destroy() {
    // 清理事件订阅
    Object.values(subscriptions).forEach((subscription) => {
      if (subscription && typeof subscription.unsubscribe === "function") {
        subscription.unsubscribe();
      }
    });

    // 清理指标
    indicatorsManager.destroyAllIndicators();

    // 清理数据管理器
    dataManager.clearData();

    // 销毁图表
    if (chart.value) {
      chart.value.remove();
      chart.value = null;
    }

    candlestickSeries.value = null;
  }

  // 组件挂载时初始化
  onMounted(() => {
    initChart();
  });

  // 组件卸载时清理
  onUnmounted(() => {
    destroy();
  });

  return {
    chartContainer,
    chart,
    candlestickSeries,
    dataManager,
    indicatorsManager,

    // 方法
    initChart,
    updateChartData,
    recalculateAllIndicators,
    initIndicators,
    resize,
    addMarker,
    addLineSeries,
    destroy,

    // 性能统计
    getPerformanceStats: indicatorsManager.getPerformanceStats,
    getDataStats: dataManager.getStats,
  };
}
