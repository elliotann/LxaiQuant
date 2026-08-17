import { get, post, put, del } from "./base";

// 策略类型定义
export interface Strategy {
  id: string;
  name: string;
  description?: string;
  type?: string;
  status?: string;
  code?: string;
  language?: string;
  timeFrame?: string; // 后端策略时间框架字段
  createdAt?: string;
  updatedAt?: string;
}

// 分页信息接口
export interface PaginationInfo {
  total: number;
  page: number;
  limit: number;
  pages: number;
}

// API响应通用接口
interface ApiResponse<T = any> {
  success?: boolean;
  message?: string;
  data?: T;
  [key: string]: any; // 允许其他属性
}

// 策略列表响应接口
interface StrategiesResponse {
  strategies?: Strategy[];
  pagination?: PaginationInfo;
  success?: boolean;
  data?: {
    strategies: Strategy[];
    pagination: PaginationInfo;
  };
}

// 策略详情响应接口
interface StrategyResponse {
  strategy?: Strategy;
  success?: boolean;
  data?: {
    strategy: Strategy;
  };
  [key: string]: any;
}

// 获取策略列表
export const getStrategies = async (params?: {
  page?: number;
  limit?: number;
  search?: string;
  status?: string;
  type?: string;
}): Promise<{ strategies: Strategy[]; pagination: PaginationInfo }> => {
  console.log("🔥 getStrategies API called with params:", params);

  const response: StrategiesResponse = await get("/strategies", { params });

  console.log("🔥 getStrategies API response:", response);
  console.log("🔥 getStrategies response structure:", {
    hasStrategies: "strategies" in response,
    hasPagination: "pagination" in response,
    hasData: "data" in response,
    hasSuccess: "success" in response,
    strategies: response.strategies,
    pagination: response.pagination,
    data: response.data,
    success: response.success,
  });

  // 处理不同的响应结构
  if (response.success !== undefined && response.data) {
    // 新结构：{ success: true, data: { strategies: [...], pagination: {...} } }
    console.log("🔥 getStrategies: 使用新响应结构");
    return {
      strategies: response.data.strategies || [],
      pagination: response.data.pagination || {
        total: 0,
        page: 1,
        limit: 10,
        pages: 1,
      },
    };
  } else if (response.strategies && response.pagination) {
    // 旧结构：{ strategies: [...], pagination: {...} }
    console.log("🔥 getStrategies: 使用旧响应结构");
    return {
      strategies: response.strategies,
      pagination: response.pagination,
    };
  } else {
    console.error("🔥 getStrategies: 未知的响应结构:", response);
    // 返回默认结构
    return {
      strategies: [],
      pagination: { total: 0, page: 1, limit: 10, pages: 1 },
    };
  }
};

// 获取策略详情
export const getStrategyById = async (id: string): Promise<Strategy> => {
  console.log("🔥 getStrategyById API called with id:", id);

  const response: StrategyResponse = await get(`/strategies/${id}`);

  console.log("🔥 getStrategyById API response:", response);
  console.log("🔥 getStrategyById response structure:", {
    hasStrategy: "strategy" in response,
    hasData: "data" in response,
    hasSuccess: "success" in response,
    strategy: response.strategy,
    data: response.data,
    success: response.success,
  });

  // 处理不同的响应结构
  if (response.success !== undefined && response.data) {
    // 新结构：{ success: true, data: { strategy: {...} } }
    console.log("🔥 getStrategyById: 使用新响应结构");
    return response.data.strategy || response.data;
  } else if (response.strategy) {
    // 旧结构：{ strategy: {...} }
    console.log("🔥 getStrategyById: 使用旧响应结构");
    return response.strategy;
  } else {
    // 直接返回响应（假设响应本身就是策略对象）
    console.log("🔥 getStrategyById: 直接返回响应");
    return response as Strategy;
  }
};

// 创建策略
export const createStrategy = async (
  data: Partial<Strategy>,
): Promise<ApiResponse> => {
  const response: ApiResponse = await post("/strategies", data);
  return response;
};

// 更新策略
export const updateStrategy = async (
  id: string,
  data: Partial<Strategy>,
): Promise<ApiResponse> => {
  const response: ApiResponse = await put(`/strategies/${id}`, data);
  return response;
};

// 删除策略
export const deleteStrategy = async (id: string): Promise<ApiResponse> => {
  const response: ApiResponse = await del(`/strategies/${id}`);
  return response;
};

// 获取策略模板
export const getStrategyTemplates = async (): Promise<ApiResponse> => {
  const response: ApiResponse = await get("/strategies/templates/list");
  return response;
};

// 验证策略代码
export const validateStrategy = async (data: {
  code: string;
  language: string;
  type: string;
}): Promise<ApiResponse> => {
  const response: ApiResponse = await post("/strategies/validate", data);
  return response;
};

// 启动策略
export const startStrategy = async (id: string): Promise<ApiResponse> => {
  const response: ApiResponse = await post(`/strategies/${id}/start`);
  return response;
};

// 停止策略
export const stopStrategy = async (id: string): Promise<ApiResponse> => {
  const response: ApiResponse = await post(`/strategies/${id}/stop`);
  return response;
};

// 更新策略状态
export const updateStrategyStatus = async (
  id: string,
  status: string,
): Promise<ApiResponse> => {
  console.log("🔥 updateStrategyStatus API called with:", { id, status });

  const response: StrategyResponse & ApiResponse = await put(
    `/strategies/${id}/status`,
    { status },
  );

  console.log("🔥 updateStrategyStatus API response:", response);
  console.log("🔥 updateStrategyStatus response structure:", {
    hasStrategy: "strategy" in response,
    hasData: "data" in response,
    hasSuccess: "success" in response,
    strategy: response.strategy,
    data: response.data,
    success: response.success,
  });

  // 处理不同的响应结构
  if (response.success !== undefined && response.data) {
    // 新结构：{ success: true, data: { strategy: {...} } }
    console.log("🔥 updateStrategyStatus: 使用新响应结构");
    return response as ApiResponse;
  } else if (response.strategy) {
    // 旧结构：{ strategy: {...} }
    console.log("🔥 updateStrategyStatus: 使用旧响应结构");
    return {
      success: true,
      message: "Strategy status updated successfully",
      data: { strategy: response.strategy },
    };
  } else {
    // 直接返回响应
    console.log("🔥 updateStrategyStatus: 直接返回响应");
    return response as ApiResponse;
  }
};

// 获取策略性能
export const getStrategyPerformance = async (
  id: string,
): Promise<ApiResponse> => {
  const response: ApiResponse = await get(`/strategies/${id}/performance`);
  return response;
};

// 获取策略日志
export const getStrategyLogs = async (
  id: string,
  params?: {
    page?: number;
    limit?: number;
    level?: string;
  },
): Promise<ApiResponse> => {
  const response: ApiResponse = await get(`/strategies/${id}/logs`, { params });
  return response;
};

// 获取策略交易记录
export const getStrategyTrades = async (
  id: string,
  params?: {
    page?: number;
    limit?: number;
  },
): Promise<ApiResponse> => {
  const response: ApiResponse = await get(`/strategies/${id}/trades`, {
    params,
  });
  return response;
};

// 复制策略
export const duplicateStrategy = async (id: string): Promise<ApiResponse> => {
  const response: ApiResponse = await post(`/strategies/${id}/duplicate`);
  return response;
};

/**
 * 策略类型选项（用于下拉框）
 */
export interface StrategyTypeOption {
  value: string;
  label: string;
  description: string;
}

/**
 * 查询所有策略信息并转换为下拉选项格式
 */
export const getAllStrategyTypes = async (): Promise<StrategyTypeOption[]> => {
  const response: StrategiesResponse = await get("/strategies", {
    params: { page: 1, limit: 1000 },
  });

  if (
    response.success &&
    response.data &&
    response.data.strategies &&
    Array.isArray(response.data.strategies)
  ) {
    // 将策略对象列表转换为下拉选项格式
    return response.data.strategies
      .map((strategy: Strategy) => ({
        value: strategy.id || "",
        label: strategy.name || "未命名策略",
        description: strategy.description || "",
      }))
      .filter((item: StrategyTypeOption) => item.value); // 过滤掉没有ID的策略
  } else if (response.strategies && Array.isArray(response.strategies)) {
    // 处理旧结构
    return response.strategies
      .map((strategy: Strategy) => ({
        value: strategy.id || "",
        label: strategy.name || "未命名策略",
        description: strategy.description || "",
      }))
      .filter((item: StrategyTypeOption) => item.value);
  } else {
    return [];
  }
};

// AI策略生成接口
export interface AiGenerateRequest {
  prompt: string;
  intent?: string;
  marketType?: string;
}

// AI策略推荐结果
export interface AiStrategyRecommendation {
  botType: string;
  reason: string;
  baseConfig: {
    symbol: string;
    timeframe: string;
    marketType: string;
    leverage: number;
    initialCapital: number;
  };
  strategyParams: Record<string, any>;
  riskConfig: {
    maxDrawdownPct: number;
    maxPositionPct: number;
    dailyLossLimitPct: number;
  };
}

// AI确认创建请求
export interface AiConfirmRequest {
  recommendation: AiStrategyRecommendation;
  botName: string;
  remark?: string;
  userId: string;
  accountId: string;
}

// 调用AI生成策略推荐
export const aiGenerateStrategy = async (
  data: AiGenerateRequest,
): Promise<ApiResponse<AiStrategyRecommendation>> => {
  const response: ApiResponse = await post("/strategy/ai-generate", data);
  return response;
};

// 确认AI推荐并创建策略+机器人
export const aiConfirmStrategy = async (
  data: AiConfirmRequest,
): Promise<ApiResponse> => {
  const response: ApiResponse = await post("/strategy/ai-confirm", data);
  return response;
};

export default {
  getStrategies,
  getStrategyById,
  createStrategy,
  updateStrategy,
  deleteStrategy,
  getStrategyTemplates,
  validateStrategy,
  startStrategy,
  stopStrategy,
  getStrategyPerformance,
  getStrategyLogs,
  getStrategyTrades,
  updateStrategyStatus,
  duplicateStrategy,
  getAllStrategyTypes,
  aiGenerateStrategy,
  aiConfirmStrategy,
};
