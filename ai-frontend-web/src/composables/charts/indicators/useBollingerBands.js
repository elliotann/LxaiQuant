import { ref, onUnmounted, readonly } from "vue";
import { CalculationEngine } from "../PerformanceOptimizer";

export function useBollingerBands(chart, options = {}) {
  const series = ref({
    middle: null,
    upper: null,
    lower: null,
  });

  const defaultOptions = {
    period: 20,
    multiplier: 2,
    colors: {
      middle: "#2196F3",
      upper: "#FF6B6B",
      lower: "#4ECDC4",
    },
    ...options,
  };

  // 计算布林带数据
  function calculateBoll(
    data,
    period = defaultOptions.period,
    multiplier = defaultOptions.multiplier,
  ) {
    if (!data || data.length < period) return [];

    const result = [];

    for (let i = period - 1; i < data.length; i++) {
      // 使用优化后的SMA计算
      const closes = data.slice(i - period + 1, i + 1).map((d) => d.close);
      const ma = CalculationEngine.calculateSMA(closes, period)[period - 1];

      // 计算标准差
      let variance = 0;
      for (let j = i - period + 1; j <= i; j++) {
        variance += Math.pow(data[j].close - ma, 2);
      }
      const stdDev = Math.sqrt(variance / period);

      // 计算上下轨
      const upper = ma + multiplier * stdDev;
      const lower = ma - multiplier * stdDev;

      result.push({
        time: data[i].time,
        middle: ma,
        upper: upper,
        lower: lower,
      });
    }

    return result;
  }

  // 初始化系列
  function init() {
    if (!chart.value) return;

    // 创建中轨线
    series.value.middle = chart.value.addLineSeries({
      color: defaultOptions.colors.middle,
      lineWidth: 0,
      title: "",
      priceLineVisible: false,
      lastValueVisible: true,
    });

    // 创建上轨线
    series.value.upper = chart.value.addLineSeries({
      color: defaultOptions.colors.upper,
      lineWidth: 0,
      title: "",
      priceLineVisible: false,
      lastValueVisible: true,
    });

    // 创建下轨线
    series.value.lower = chart.value.addLineSeries({
      color: defaultOptions.colors.lower,
      lineWidth: 0,
      title: "",
      priceLineVisible: false,
      lastValueVisible: true,
    });
  }

  // 更新数据
  function update(data, config) {
    if (!chart.value || !series.value.middle) return;

    const options = { ...defaultOptions, ...config };

    if (options.enabled && data && data.length > 0) {
      const bollData = calculateBoll(data, options.period, options.multiplier);

      if (bollData.length > 0) {
        // 更新中轨
        series.value.middle.setData(
          bollData.map((d) => ({ time: d.time, value: d.middle })),
        );

        // 更新上轨
        series.value.upper.setData(
          bollData.map((d) => ({ time: d.time, value: d.upper })),
        );

        // 更新下轨
        series.value.lower.setData(
          bollData.map((d) => ({ time: d.time, value: d.lower })),
        );
      }
    } else {
      // 清空数据
      series.value.middle.setData([]);
      series.value.upper.setData([]);
      series.value.lower.setData([]);
    }
  }

  // 清理资源
  function destroy() {
    if (series.value.middle) {
      chart.value.removeSeries(series.value.middle);
      series.value.middle = null;
    }
    if (series.value.upper) {
      chart.value.removeSeries(series.value.upper);
      series.value.upper = null;
    }
    if (series.value.lower) {
      chart.value.removeSeries(series.value.lower);
      series.value.lower = null;
    }
  }

  // 组件卸载时自动清理
  onUnmounted(destroy);

  return {
    series: readonly(series),
    init,
    update,
    destroy,
    calculateBoll,
  };
}
