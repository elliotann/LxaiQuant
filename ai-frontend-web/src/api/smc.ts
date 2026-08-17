import api from "./base";

export const getSmc = (params: {
  symbol: string;
  interval: string;
  from: number;
  to: number;
}) => api.get("/smc", { params });

// 获取多周期SMC行情看板数据
export const getMultiPeriod = (symbol: string) =>
  api.get("/smc/multiPeriod", { params: { symbol } });

// 获取多周期结构评估数据（波次、位置比、混沌特例等）
export const getEvaluate = (symbol: string) =>
  api.get("/smc/evaluate", { params: { symbol } });

export default { getSmc, getMultiPeriod, getEvaluate };

