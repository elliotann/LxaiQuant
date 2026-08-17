// 性能优化工具类
export class PerformanceOptimizer {
  constructor() {
    this.updateQueue = new Map();
    this.updateInterval = 100; // ms
    this.batchTimer = null;
    this.renderThrottle = 16; // ~60fps
    this.lastRenderTime = 0;
    this.indicatorCache = new Map();
    this.cacheVersion = 0;
  }

  // 批量更新指标
  batchUpdate(updateFn, key) {
    if (!this.updateQueue.has(key)) {
      this.updateQueue.set(key, updateFn);
    }

    if (!this.batchTimer) {
      this.batchTimer = setTimeout(() => {
        this.updateQueue.forEach((fn) => fn());
        this.updateQueue.clear();
        this.batchTimer = null;
      }, this.updateInterval);
    }
  }

  // 请求动画帧节流
  throttleWithRAF(callback) {
    const now = Date.now();
    if (now - this.lastRenderTime < this.renderThrottle) {
      return;
    }
    this.lastRenderTime = now;

    requestAnimationFrame(callback);
  }

  // 防抖函数
  debounce(func, wait) {
    let timeout;
    return function executedFunction(...args) {
      const later = () => {
        clearTimeout(timeout);
        func(...args);
      };
      clearTimeout(timeout);
      timeout = setTimeout(later, wait);
    };
  }

  // 计算缓存
  calculateWithCache(key, calculateFn, ...args) {
    const cacheKey = `${key}_${this.cacheVersion}`;

    if (this.indicatorCache.has(cacheKey)) {
      return this.indicatorCache.get(cacheKey);
    }

    const result = calculateFn(...args);
    this.indicatorCache.set(cacheKey, result);
    return result;
  }

  // 清除缓存
  clearCache() {
    this.cacheVersion++;
    this.indicatorCache.clear();
  }

  // 性能监控
  static start(name) {
    if (!this.timers) this.timers = {};
    this.timers[name] = performance.now();
  }

  static end(name) {
    if (this.timers && this.timers[name]) {
      const duration = performance.now() - this.timers[name];
      if (duration > 16) {
        // 超过一帧的时间
        console.warn(`性能警告: ${name} 耗时 ${duration.toFixed(2)}ms`);
      }
      delete this.timers[name];
      return duration;
    }
    return 0;
  }

  // 内存清理
  cleanup() {
    this.updateQueue.clear();
    if (this.batchTimer) {
      clearTimeout(this.batchTimer);
      this.batchTimer = null;
    }
    this.indicatorCache.clear();
  }
}

// 通用计算引擎
export class CalculationEngine {
  // 使用更高效的算法计算EMA
  static calculateEMA(data, period) {
    if (!data || data.length < period) return [];

    const multiplier = 2 / (period + 1);
    const result = new Array(data.length);
    result[0] = data[0];

    for (let i = 1; i < data.length; i++) {
      result[i] = (data[i] - result[i - 1]) * multiplier + result[i - 1];
    }

    return result;
  }

  // 计算SMA
  static calculateSMA(data, period) {
    if (!data || data.length < period) return [];

    const result = new Array(data.length);
    let sum = 0;

    // 初始化前period个数据
    for (let i = 0; i < period - 1; i++) {
      sum += data[i];
      result[i] = sum / (i + 1); // 部分平均值
    }

    // 计算完整的SMA
    sum = 0;
    for (let i = 0; i < period; i++) {
      sum += data[i];
    }
    result[period - 1] = sum / period;

    for (let i = period; i < data.length; i++) {
      sum = sum - data[i - period] + data[i];
      result[i] = sum / period;
    }

    return result;
  }

  // 批量计算多个指标
  static batchCalculateIndicators(data, indicators) {
    const results = {};

    indicators.forEach((indicator) => {
      switch (indicator.type) {
        case "SMA":
          results[indicator.name] = this.calculateSMA(data, indicator.period);
          break;
        case "EMA":
          results[indicator.name] = this.calculateEMA(data, indicator.period);
          break;
        // 可以扩展其他指标类型
      }
    });

    return results;
  }

  // 标准化数据格式
  static normalizeData(data) {
    return data
      .filter((item) => {
        return (
          item &&
          item.time != null &&
          item.open != null &&
          item.high != null &&
          item.low != null &&
          item.close != null &&
          !isNaN(item.open) &&
          !isNaN(item.high) &&
          !isNaN(item.low) &&
          !isNaN(item.close) &&
          isFinite(item.open) &&
          isFinite(item.high) &&
          isFinite(item.low) &&
          isFinite(item.close)
        );
      })
      .map((item) => ({
        time: item.time,
        open: item.open,
        high: item.high,
        low: item.low,
        close: item.close,
      }))
      .sort((a, b) => a.time - b.time); // 确保时间升序
  }
}
