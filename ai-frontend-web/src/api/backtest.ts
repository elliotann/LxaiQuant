import { get, post, put } from "./base";

// 回测配置接口
export interface BacktestConfig {
  strategyId: string;
  name: string;
  description?: string;
  symbols: string[];
  timeframe: string;
  startDate: Date;
  endDate: Date;
  initialCapital: number;
  commission?: number;
  slippage?: number;
  leverage?: number;
  riskLimits?: string[];
  outputOptions?: string[];
  params?: Record<string, any>;
}

// 回测结果接口
export interface BacktestResult {
  id: string;
  name: string;
  strategyId: string;
  status: "pending" | "running" | "completed" | "failed";
  progress: number;
  config: BacktestConfig;
  metrics?: {
    totalReturn: number;
    annualizedReturn: number;
    sharpeRatio: number;
    maxDrawdown: number;
    winRate: number;
    profitFactor: number;
    totalTrades: number;
  };
  trades?: any[];
  error?: string;
  createdAt: Date;
  updatedAt: Date;
}

// 启动回测
export const startBacktest = async (config: BacktestConfig) => {
  const response = await post("/backtest-v2/", config);
  return response;
};

// 获取回测状态
export const getBacktestStatus = async (id: string) => {
  const response = await get(`/backtest-v2/${id}`);
  return response;
};

// 获取异步回测任务状态
export const getBacktestTaskStatus = async (taskId: string) => {
  return await get(`/backtest/async/task/${taskId}`);
};

// 获取回测任务详情
export const getBacktestTaskDetail = async (taskId: string) => {
  const response = await get(`/backtest/tasks/${taskId}`);
  return response;
};

// 获取异步回测结果
export const getAsyncBacktestResult = async (taskId: string) => {
  return await get(`/backtest/async/result/${taskId}`);
};

// 获取回测结果
export const getBacktestResults = async (id: string) => {
  const response = await get(`/backtest/result/${id}`);
  return response;
};

// 获取收益统计数据
export const getBacktestPerformance = async (
  id: string,
  period: "daily" | "monthly" = "daily",
) => {
  const response = await get(`/backtest/performance/${id}`, {
    params: { period },
  });
  return response;
};

// 取消回测
export const cancelBacktest = async (id: string) => {
  const response = await post(`/backtest-v2/${id}/cancel`);
  return response;
};

// 获取回测历史
export const getBacktestHistory = async (params?: {
  strategyId?: string;
  page?: number;
  limit?: number;
  status?: string;
}) => {
  // 转换参数名以匹配后端接口
  const apiParams = {
    page: params?.page,
    size: params?.limit,
    status: params?.status,
    createdBy: params?.strategyId,
  };
  const response = await get("/backtest/tasks/list", { params: apiParams });
  return response;
};

// 删除回测记录
export const deleteBacktest = async (id: string) => {
  const response = await post(`/backtest-v2/${id}/cancel`);
  return response;
};

// 导出回测报告
export const exportBacktestReport = async (
  id: string,
  format: "pdf" | "excel" = "pdf",
) => {
  const response = await get(`/backtest/${id}/export/${format}`, {
    responseType: "blob",
  });
  return response;
};

// 获取支持的策略类型
export const getSupportedBacktestTypes = async () => {
  try {
    const response = await fetch("/api/backtest/strategies");
    if (response.ok) {
      return await response.json();
    } else {
      throw new Error("获取策略类型失败");
    }
  } catch (error) {
    console.error("获取策略类型失败:", error);
    return [];
  }
};

// 获取支持的回测类型
export const getBacktestTypes = async () => {
  return [
    {
      value: "TRADITIONAL_BACKTEST_NEW",
      label: "V2快速回测",
      description: "基于K线步进的回测方法(使用V2自研引擎)",
    },
    {
      value: "PAPER_TRADING",
      label: "模拟实盘",
      description: "逐根K线推送模拟实时行情，使用PaperEngine验证事件驱动链路",
    },
  ];
};

// 获取可用数据源
export const getAvailableDataSources = async () => {
  const response = await get("/data/sources");
  return response;
};

// 获取支持的时间周期
export const getSupportedTimeframes = async () => {
  const response = await get("/data/timeframes");
  return response;
};

// 验证数据可用性
export const validateDataAvailability = async (params: {
  symbol: string;
  timeframe: string;
  startDate: Date;
  endDate: Date;
}) => {
  const response = await post("/data/validate", params);
  return response;
};

// 获取回测统计信息
export const getBacktestStats = async () => {
  const response = await get("/backtest-v2/stats/");
  return response;
};

// 执行异步回测
export const runAsyncBacktest = async (params: {
  strategyType: string;
  dataSourceType?: string;
  coinId?: string;
  days?: number;
  initialAmount?: number;
  leverage?: number;
  isContractTrading?: boolean;
  commissionRate?: number;
  slippageRate?: number;
  robotId?: string;
}) => {
  const response = await post("/backtest/async/create", params);
  return response;
};

/**
 * 停止异步回测任务
 */
export const stopAsyncBacktest = async (taskId: string) => {
  const response = await put(`/backtest/${taskId}/stop`);
  return response;
};

// 获取回测交易记录
export const getBacktestTrades = async (
  backtestId: string,
  params?: {
    page?: number;
    limit?: number;
  },
) => {
  const response = await get(`/backtest-v2/${backtestId}/trades`, { params });
  return response;
};

// 获取回测模板
export const getBacktestTemplates = async () => {
  const response = await get("/backtest-v2/templates/");
  return response;
};

// 批量启动回测（参数优化）
export const startBatchBacktest = async (configs: BacktestConfig[]) => {
  const response = await post("/backtest/batch", { configs });
  return response;
};

// 参数范围优化
export const runParameterRangeOptimization = async (params: {
  strategyId: string;
  coinId: string;
  days?: number;
  startTime?: number;
  endTime?: number;
  initialAmount?: number;
  leverage?: number;
  isContractTrading?: boolean;
  commissionRate?: number;
  slippageRate?: number;
  executionMatchPolicy?: string;
  positionAdjusterId?: string;
  topK?: number;
  maxCombinations?: number;
  optimizationTarget?: string;
  ranges?: Array<{
    name: string;
    min: number;
    max: number;
    step: number;
  }>;
  parameterNames?: string[];
  signalDataFrom?: string;
  signalSymbol?: string;
  signalIndicatorType?: string;
}) => {
  const response = await post("/optimization/task", params as any);
  return response;
};

// 获取参数优化结果
export const getOptimizationResults = async (batchId: string) => {
  const response = await get(`/backtest/optimization/${batchId}`);
  return response;
};

export const getOptimizationTaskProgress = async (taskId: string) => {
  return await get(`/optimization/task/${taskId}/progress`);
};

export const getOptimizationTaskResults = async (
  taskId: string,
  limit: number = 10,
) => {
  return await get(`/optimization/task/${taskId}/results`, {
    params: { limit },
  });
};

// WebSocket 连接用于实时更新
export const createBacktestWebSocket = (backtestId: string) => {
  const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
  const wsUrl = `${protocol}//${window.location.host}/ws/backtest/${backtestId}`;
  return new WebSocket(wsUrl);
};

// 生成回测报告
export const generateBacktestReport = async (taskId: string) => {
  return await post(`/backtest/report/generate/${taskId}`);
};

// 获取回测报告
export const getBacktestReport = async (taskId: string) => {
  return await get(`/backtest/report/${taskId}`);
};

// 更新报告笔记
export const updateReportNotes = async (taskId: string, notes: string) => {
  return await put(`/backtest/report/notes/${taskId}`, { notes });
};

// 默认导出所有回测API
export default {
  startBacktest,
  getBacktestResults,
  getBacktestPerformance,
  getBacktestStatus,
  getBacktestTaskStatus,
  getAsyncBacktestResult,
  cancelBacktest,
  getBacktestHistory,
  deleteBacktest,
  exportBacktestReport,
  getSupportedBacktestTypes,
  getBacktestTypes,
  getAvailableDataSources,
  getSupportedTimeframes,
  validateDataAvailability,
  getBacktestStats,
  getBacktestTrades,
  getBacktestTemplates,
  startBatchBacktest,
  runParameterRangeOptimization,
  getOptimizationResults,
  getOptimizationTaskProgress,
  getOptimizationTaskResults,
  createBacktestWebSocket,
  generateBacktestReport,
  getBacktestReport,
  updateReportNotes,
  getBacktestTaskDetail,
};
