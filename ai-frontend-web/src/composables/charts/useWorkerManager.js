import { ref, reactive, onUnmounted } from "vue";

export function useWorkerManager() {
  const workers = reactive(new Map());
  const activeTasks = reactive(new Map());

  // 创建Worker实例
  function createWorker() {
    if (!window.Worker) {
      console.warn("Web Worker not supported");
      return null;
    }

    try {
      const worker = new Worker("/workers/indicator-worker.js");
      const workerId = Date.now() + "_" + Math.random();

      workers.set(workerId, {
        worker,
        busy: false,
        lastUsed: Date.now(),
      });

      return workerId;
    } catch (error) {
      console.error("Failed to create worker:", error);
      return null;
    }
  }

  // 获取或创建可用的Worker
  function getAvailableWorker() {
    // 查找空闲的Worker
    for (const [id, workerInfo] of workers) {
      if (!workerInfo.busy) {
        return id;
      }
    }

    // 如果没有空闲的Worker且数量不超过限制，创建新的
    if (workers.size < 4) {
      // 最多4个Worker
      return createWorker();
    }

    // 返回最久未使用的Worker
    let oldestId = null;
    let oldestTime = Date.now();

    for (const [id, workerInfo] of workers) {
      if (workerInfo.lastUsed < oldestTime) {
        oldestTime = workerInfo.lastUsed;
        oldestId = id;
      }
    }

    return oldestId;
  }

  // 执行计算任务
  function executeTask(type, data, config = {}) {
    return new Promise((resolve, reject) => {
      const workerId = getAvailableWorker();

      if (!workerId) {
        reject(new Error("No available workers"));
        return;
      }

      const workerInfo = workers.get(workerId);
      if (!workerInfo) {
        reject(new Error("Worker not found"));
        return;
      }

      workerInfo.busy = true;
      workerInfo.lastUsed = Date.now();

      const taskId = Date.now() + "_" + Math.random();

      // 设置消息处理器
      const messageHandler = (e) => {
        const { id, success, result, error, timestamp } = e.data;

        if (id === taskId) {
          // 清理事件监听器
          workerInfo.worker.removeEventListener("message", messageHandler);

          // 标记Worker为空闲
          workerInfo.busy = false;

          // 清理任务记录
          activeTasks.delete(taskId);

          if (success) {
            resolve(result);
          } else {
            reject(new Error(error));
          }
        }
      };

      // 设置错误处理器
      const errorHandler = (error) => {
        workerInfo.worker.removeEventListener("message", messageHandler);
        workerInfo.worker.removeEventListener("error", errorHandler);
        workerInfo.busy = false;
        activeTasks.delete(taskId);
        reject(error);
      };

      workerInfo.worker.addEventListener("message", messageHandler);
      workerInfo.worker.addEventListener("error", errorHandler);

      // 记录活跃任务
      activeTasks.set(taskId, {
        workerId,
        type,
        startTime: Date.now(),
      });

      // 发送任务到Worker
      workerInfo.worker.postMessage({
        id: taskId,
        type,
        data,
        config,
      });

      // 设置超时（30秒）
      setTimeout(() => {
        if (activeTasks.has(taskId)) {
          workerInfo.worker.removeEventListener("message", messageHandler);
          workerInfo.worker.removeEventListener("error", errorHandler);
          workerInfo.busy = false;
          activeTasks.delete(taskId);
          reject(new Error("Task timeout"));
        }
      }, 30000);
    });
  }

  // 批量计算指标
  async function batchCalculateIndicators(data, indicators) {
    if (!data || indicators.length === 0) return {};

    try {
      const result = await executeTask("BATCH", data, { indicators });
      return result;
    } catch (error) {
      console.warn(
        "Batch calculation failed, falling back to main thread:",
        error,
      );

      // 降级到主线程计算
      const results = {};
      indicators.forEach((indicator) => {
        try {
          switch (indicator.type) {
            case "BOLL":
              // 这里需要导入相应的计算函数
              // results[indicator.name] = calculateBollingerBands(data, indicator.period, indicator.multiplier)
              break;
            case "MACD":
              // results[indicator.name] = calculateMACD(data, indicator.fastPeriod, indicator.slowPeriod, indicator.signalPeriod)
              break;
          }
        } catch (calcError) {
          console.error(`Failed to calculate ${indicator.name}:`, calcError);
        }
      });

      return results;
    }
  }

  // 计算布林带
  async function calculateBollingerBands(data, period, multiplier) {
    try {
      return await executeTask("BOLL", data, { period, multiplier });
    } catch (error) {
      console.warn("Worker calculation failed for BOLL:", error);
      // 降级处理可以在调用方实现
      throw error;
    }
  }

  // 计算MACD
  async function calculateMACD(data, fastPeriod, slowPeriod, signalPeriod) {
    try {
      return await executeTask("MACD", data, {
        fastPeriod,
        slowPeriod,
        signalPeriod,
      });
    } catch (error) {
      console.warn("Worker calculation failed for MACD:", error);
      throw error;
    }
  }

  // 计算RSI
  async function calculateRSI(data, period) {
    try {
      return await executeTask("RSI", data, { period });
    } catch (error) {
      console.warn("Worker calculation failed for RSI:", error);
      throw error;
    }
  }

  // 计算多时间框架趋势
  async function calculateMultiTimeframeTrend(data, config) {
    try {
      return await executeTask("MULTI_TIMEFRAME_TREND", data, config);
    } catch (error) {
      console.warn("Worker calculation failed for MultiTimeframeTrend:", error);
      throw error;
    }
  }

  // 获取统计信息
  function getStats() {
    return {
      totalWorkers: workers.size,
      busyWorkers: Array.from(workers.values()).filter((w) => w.busy).length,
      activeTasks: activeTasks.size,
      workers: Array.from(workers.entries()).map(([id, info]) => ({
        id,
        busy: info.busy,
        lastUsed: info.lastUsed,
      })),
    };
  }

  // 清理资源
  function cleanup() {
    // 终止所有Worker
    workers.forEach((workerInfo, id) => {
      try {
        workerInfo.worker.terminate();
      } catch (error) {
        console.error(`Failed to terminate worker ${id}:`, error);
      }
    });

    workers.clear();
    activeTasks.clear();
  }

  // 组件卸载时清理
  onUnmounted(() => {
    cleanup();
  });

  return {
    executeTask,
    batchCalculateIndicators,
    calculateBollingerBands,
    calculateMACD,
    calculateRSI,
    calculateMultiTimeframeTrend,
    getStats,
    cleanup,
  };
}
