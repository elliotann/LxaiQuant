import { get, post, put, del } from "./base";

// K线数据相关API
export const getklineData = async (params: any) => {
  return await post("/kline/data", params);
};

export const getUnshiftData = async (params: any) => {
  return await get("/kline/unshift", { params });
};

export const getLatestKlineData = async (params: any) => {
  return await post("/kline/latest-data", params);
};

export const listSelect = async (params: any) => {
  return await get("/member/list-select", { params });
};

export const getTrendAnalysis = async (params: any) => {
  return await get("/member/trend-analysis", { params });
};

export const getElliottWaveAnalysis = async (params: any) => {
  return await get("/member/elliott-wave-analysis", { params });
};

export const getMultiTimeframeTrend = async (params: any) => {
  return await get("/member/multi-timeframe-trend", { params });
};

export const getLogRegChannelIndicator = async (params: any) => {
  return await get("/trading/trend/logreg-channel", { params });
};

// 回测相关API
export const runBacktest = async (data: any) => {
  return await post("/member/backtest", data);
};

export const getBacktestResult = async (id: string) => {
  return await get(`/member/backtest/${id}`);
};

export const getBacktestRecords = async (params: any) => {
  // 调用已有的回测任务列表接口，strategyId 映射为 strategyCode
  const { strategyId, ...rest } = params;
  return await get("/backtest/tasks/list", { params: { strategyCode: strategyId, page: 1, size: 50, ...rest } });
};

export const deleteBacktestRecord = async (id: string) => {
  return await del(`/member/backtest-record/${id}`);
};

export const stopBacktest = async (id: string) => {
  return await put(`/member/backtest/${id}/stop`);
};

export const getBacktestProgress = async (id: string) => {
  return await get(`/member/backtest/${id}/progress`);
};

export const getEquityPoints = async (params: any) => {
  return await get("/member/equity-points", { params });
};
