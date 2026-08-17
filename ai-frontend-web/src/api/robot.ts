import { get, post, put, del } from "./base";

// 获取机器人列表
export const getAllRobotList = async (params?: any) => {
  return await get("/robot/list", { params });
};

// 创建机器人
export const createRobot = async (data: any) => {
  return await post("/robot", data);
};

// 更新机器人
export const updateRobot = async (id: string, data: any) => {
  return await put(`/robot/${id}`, data);
};

// 删除机器人
export const deleteRobot = async (id: string) => {
  return await del(`/robot/${id}`);
};

// 获取机器人详情
export const getRobotById = async (id: string) => {
  return await get(`/robot/${id}`);
};

// 启动机器人
export const startRobot = async (id: string) => {
  return await put(`/robot/${id}/start`);
};

// 停止机器人
export const stopRobot = async (id: string) => {
  return await put(`/robot/${id}/stop`);
};

// ==================== 交易机器人相关接口 ====================

// 分页查询交易机器人
export const getTradingBots = async (params?: any) => {
  return await get("/trading-bots", { params });
};

// 获取交易机器人详情
export const getTradingBotById = async (botId: string) => {
  return await get(`/trading-bots/${botId}`);
};

// 创建交易机器人
export const createTradingBot = async (data: any) => {
  return await post("/trading-bots", data);
};

// 更新交易机器人
export const updateTradingBot = async (botId: string, data: any) => {
  return await put(`/trading-bots/${botId}`, data);
};

// 删除交易机器人
export const deleteTradingBot = async (botId: string) => {
  return await del(`/trading-bots/${botId}`);
};

// 启动交易机器人
export const startBot = async (botId: string) => {
  return await post(`/trading-bots/${botId}/start`);
};

// 停止交易机器人
export const stopBot = async (botId: string) => {
  return await post(`/trading-bots/${botId}/stop`);
};

// 暂停交易机器人
export const pauseBot = async (botId: string) => {
  return await post(`/trading-bots/${botId}/pause`);
};

// 恢复交易机器人
export const resumeBot = async (botId: string) => {
  return await post(`/trading-bots/${botId}/resume`);
};

// 更新机器人状态
export const updateBotStatus = async (botId: string, status: string) => {
  return await post(`/trading-bots/${botId}/status`, { status });
};

// 批量更新机器人状态
export const batchUpdateBotStatus = async (
  botIds: string[],
  status: string,
) => {
  return await post("/trading-bots/batch/status", { botIds, status });
};

// 根据用户ID查询机器人列表
export const getBotsByUserId = async (userId: string) => {
  return await get(`/trading-bots/user/${userId}`);
};

// 根据策略ID查询机器人列表
export const getBotsByStrategyId = async (strategyId: string) => {
  return await get(`/trading-bots/strategy/${strategyId}`);
};

// 根据账户ID查询机器人列表
export const getBotsByAccountId = async (accountId: string) => {
  return await get(`/trading-bots/account/${accountId}`);
};

// 根据状态查询机器人列表
export const getBotsByStatus = async (status: string) => {
  return await get(`/trading-bots/status/${status}`);
};

// 更新机器人资金
export const updateBotCapital = async (
  botId: string,
  allocatedCapital: number,
  currentCapital: number,
) => {
  return await post(`/trading-bots/${botId}/capital`, {
    allocatedCapital,
    currentCapital,
  });
};

// 更新机器人统计信息
export const updateBotStatistics = async (
  botId: string,
  statistics: string,
) => {
  return await post(`/trading-bots/${botId}/statistics`, { statistics });
};

// 获取机器人运行状态详情
export const getBotRunningStatus = async (botId: string) => {
  return await get(`/trading-bots/${botId}/running-status`);
};

// 获取机器人状态统计
export const getBotStatusStats = async () => {
  return await get("/trading-bots/stats/status");
};

// ==================== 机器人权益对比 ====================

export const getRobotEquityCompare = async (params: {
  robotIds: string[];
  startDate: string;
  endDate: string;
  alignType?: string;
}) => {
  return await get("/robot/equity/compare", {
    params: {
      robotIds: params.robotIds.join(","),
      startDate: params.startDate,
      endDate: params.endDate,
      alignType: params.alignType || "absolute",
    },
  });
};

export const getRobotEquityLatest = async (robotIds?: string[]) => {
  return await get("/robot/equity/latest", {
    params: robotIds ? { robotIds: robotIds.join(",") } : {},
  });
};

// ==================== 交易机器人参数接口 ====================

/** 保存机器人某分组的所有参数（全量替换） */
export const saveBotParameters = async (
  botId: string,
  group: string,
  params: Record<string, string>,
) => {
  return await put(`/trading-bots/${botId}/parameters/${group}`, params);
};

/** 查询机器人某分组的所有参数 */
export const getBotParameters = async (
  botId: string,
  group: string,
) => {
  return await get(`/trading-bots/${botId}/parameters/${group}`);
};

// ==================== 实盘交易相关接口 ====================

// 启动实盘交易策略
export const startLiveTrading = async (data: {
  strategyName: string;
  parameters?: Record<string, any>;
  testMode?: boolean;
  startTime?: string;
  endTime?: string;
}) => {
  return await post("/live-trading/start", data);
};

// 停止实盘交易策略
export const stopLiveTrading = async (strategyName: string) => {
  return await post(`/live-trading/stop/${strategyName}`);
};

// 获取实盘交易策略状态
export const getLiveTradingStatus = async (strategyName: string) => {
  return await get(`/live-trading/status/${strategyName}`);
};
