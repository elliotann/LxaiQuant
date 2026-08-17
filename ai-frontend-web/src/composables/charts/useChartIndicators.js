import { ref, reactive, onUnmounted } from "vue";
import { PerformanceOptimizer } from "./PerformanceOptimizer";
import { useBollingerBands } from "./indicators/useBollingerBands";
import { useMACD } from "./indicators/useMACD";

export function useChartIndicators(chart) {
  const indicators = reactive({
    boll: null,
    macd: null,
    rsi: null,
    // 可以继续添加其他指标
  });

  const performanceOptimizer = new PerformanceOptimizer();

  // 注册指标
  function registerIndicator(name, IndicatorComposable, options = {}) {
    if (indicators[name]) {
      indicators[name].destroy();
    }

    const indicator = IndicatorComposable(chart, options);
    indicators[name] = indicator;

    return indicator;
  }

  // 更新单个指标
  function updateIndicator(name, config, data) {
    if (!indicators[name]) return;

    performanceOptimizer.batchUpdate(() => {
      try {
        indicators[name].update(data, config);
      } catch (error) {
        console.error(`更新指标 ${name} 时出错:`, error);
      }
    }, `update_${name}`);
  }

  // 批量更新所有启用的指标
  function updateAllIndicators(configs, data) {
    Object.entries(configs).forEach(([name, config]) => {
      if (config && config.enabled) {
        updateIndicator(name, config, data);
      }
    });
  }

  // 初始化指标
  function initIndicators(indicatorConfigs) {
    Object.entries(indicatorConfigs).forEach(([name, config]) => {
      if (config && config.enabled) {
        switch (name) {
          case "boll":
            indicators.boll = useBollingerBands(chart, config);
            indicators.boll.init();
            break;
          case "macd":
            indicators.macd = useMACD(chart, config);
            indicators.macd.init();
            break;
          // 可以继续添加其他指标的初始化
        }
      }
    });
  }

  // 清理所有指标
  function destroyAllIndicators() {
    Object.entries(indicators).forEach(([name, indicator]) => {
      if (indicator && indicator.destroy) {
        try {
          indicator.destroy();
        } catch (error) {
          console.error(`清理指标 ${name} 时出错:`, error);
        }
      }
    });

    // 清空引用
    Object.keys(indicators).forEach((key) => {
      indicators[key] = null;
    });

    performanceOptimizer.cleanup();
  }

  // 智能更新策略 - 根据变化的属性决定更新哪些指标
  function smartUpdate(changedProps, configs, data) {
    const updateQueue = [];

    changedProps.forEach((prop) => {
      switch (prop) {
        case "boll":
          if (configs.boll?.enabled) {
            updateQueue.push(() => updateIndicator("boll", configs.boll, data));
          }
          break;
        case "macd":
          if (configs.macd?.enabled) {
            updateQueue.push(() => updateIndicator("macd", configs.macd, data));
          }
          break;
        // 可以继续添加其他指标
      }
    });

    // 批量执行更新
    performanceOptimizer.batchUpdate(() => {
      updateQueue.forEach((fn) => fn());
    }, "smart_update");
  }

  // 获取性能统计
  function getPerformanceStats() {
    return {
      cacheSize: performanceOptimizer.indicatorCache.size,
      lastRenderTime: performanceOptimizer.lastRenderTime,
    };
  }

  // 组件卸载时清理
  onUnmounted(() => {
    destroyAllIndicators();
  });

  return {
    indicators,
    registerIndicator,
    updateIndicator,
    updateAllIndicators,
    initIndicators,
    destroyAllIndicators,
    smartUpdate,
    getPerformanceStats,
  };
}
