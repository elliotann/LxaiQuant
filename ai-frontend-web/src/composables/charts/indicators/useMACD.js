import { ref, onUnmounted } from "vue";
import { CalculationEngine } from "../PerformanceOptimizer";

export function useMACD(chart, options = {}) {
  const series = ref({
    macd: null,
    signal: null,
    histogram: null,
  });

  const defaultOptions = {
    fastPeriod: 12,
    slowPeriod: 26,
    signalPeriod: 9,
    colors: {
      macd: "#2196F3",
      signal: "#FF9800",
      histogram: "#4CAF50",
    },
    ...options,
  };

  // 计算MACD数据
  function calculateMACD(
    data,
    fastPeriod = defaultOptions.fastPeriod,
    slowPeriod = defaultOptions.slowPeriod,
    signalPeriod = defaultOptions.signalPeriod,
  ) {
    if (!data || data.length < slowPeriod) return [];

    const closes = data.map((d) => d.close);
    const emaFast = CalculationEngine.calculateEMA(closes, fastPeriod);
    const emaSlow = CalculationEngine.calculateEMA(closes, slowPeriod);

    const macdLine = [];
    for (let i = 0; i < closes.length; i++) {
      if (i >= slowPeriod - 1) {
        macdLine.push(emaFast[i] - emaSlow[i]);
      } else {
        macdLine.push(0);
      }
    }

    const signalLine = CalculationEngine.calculateEMA(macdLine, signalPeriod);
    const histogram = [];

    for (let i = 0; i < macdLine.length; i++) {
      histogram.push(macdLine[i] - signalLine[i]);
    }

    const result = [];
    for (let i = 0; i < data.length; i++) {
      if (i >= slowPeriod - 1) {
        result.push({
          time: data[i].time,
          macd: macdLine[i],
          signal: signalLine[i],
          histogram: histogram[i],
        });
      }
    }

    return result;
  }

  // 初始化MACD系列
  function init() {
    if (!chart.value) return;

    // 创建MACD线
    series.value.macd = chart.value.addLineSeries({
      color: defaultOptions.colors.macd,
      lineWidth: 0,
      title: "",
      priceScaleId: "macd",
      priceLineVisible: false,
      lastValueVisible: true,
    });

    // 创建信号线
    series.value.signal = chart.value.addLineSeries({
      color: defaultOptions.colors.signal,
      lineWidth: 0,
      title: "",
      priceScaleId: "macd",
      priceLineVisible: false,
      lastValueVisible: true,
    });

    // 创建柱状图
    series.value.histogram = chart.value.addHistogramSeries({
      color: defaultOptions.colors.histogram,
      title: "",
      priceScaleId: "macd",
      priceLineVisible: false,
      lastValueVisible: true,
    });

    // 配置MACD价格刻度
    chart.value.priceScale("macd").applyOptions({
      autoScale: true,
      position: "right",
      borderVisible: true,
      borderColor: "#cccccc",
    });
  }

  // 更新数据
  function update(data, config) {
    if (!chart.value || !series.value.macd) return;

    const options = { ...defaultOptions, ...config };

    if (options.enabled && data && data.length > 0) {
      const macdData = calculateMACD(
        data,
        options.fastPeriod,
        options.slowPeriod,
        options.signalPeriod,
      );

      if (macdData.length > 0) {
        // 更新MACD线
        series.value.macd.setData(
          macdData.map((d) => ({
            time: d.time,
            value: d.macd,
          })),
        );

        // 更新信号线
        series.value.signal.setData(
          macdData.map((d) => ({
            time: d.time,
            value: d.signal,
          })),
        );

        // 更新柱状图
        series.value.histogram.setData(
          macdData.map((d) => ({
            time: d.time,
            value: d.histogram,
            color: d.histogram >= 0 ? "#4CAF50" : "#F44336",
          })),
        );
      }
    } else {
      // 清空数据
      series.value.macd.setData([]);
      series.value.signal.setData([]);
      series.value.histogram.setData([]);
    }
  }

  // 清理资源
  function destroy() {
    if (series.value.macd) {
      chart.value.removeSeries(series.value.macd);
      series.value.macd = null;
    }
    if (series.value.signal) {
      chart.value.removeSeries(series.value.signal);
      series.value.signal = null;
    }
    if (series.value.histogram) {
      chart.value.removeSeries(series.value.histogram);
      series.value.histogram = null;
    }
  }

  // 组件卸载时自动清理
  onUnmounted(destroy);

  return {
    series,
    init,
    update,
    destroy,
    calculateMACD,
  };
}
