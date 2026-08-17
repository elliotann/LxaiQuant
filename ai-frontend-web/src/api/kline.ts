/**
 * K线图表API - 新版本接口
 * 保持旧接口不变，新增v1版本接口
 */
import { get, post } from "./base";

// ========== 新版本API接口 (v1) ==========

/**
 * 获取K线历史数据
 */
export const getKLineHistory = async (params: {
  symbol: string;
  interval: string;
  exchange?: string;
  limit?: number;
  startTime?: number;
  endTime?: number;
}) => {
  return await get("/v1/kline/history", { params });
};

/**
 * 按方向加载更多数据
 */
export const loadKLineData = async (params: {
  symbol: string;
  interval: string;
  exchange?: string;
  direction: "forward" | "backward";
  anchorTime: number;
  limit?: number;
  requestId?: string;
}) => {
  return await post("/v1/kline/load", params);
};

/**
 * 批量加载数据
 */
export const loadKLineDataBatch = async (
  requests: Array<{
    symbol: string;
    interval: string;
    exchange?: string;
    direction: "forward" | "backward";
    anchorTime: number;
    limit?: number;
    requestId: string;
  }>,
) => {
  return await post("/v1/kline/load/batch", requests);
};

/**
 * 计算技术指标
 */
export const calculateIndicator = async (params: {
  symbol: string;
  interval: string;
  indicatorType: string;
  parameters: Record<string, any>;
}) => {
  return await post("/v1/kline/indicator", params);
};

/**
 * 获取支持的交易对
 */
export const getSupportedSymbols = async () => {
  return await get("/v1/kline/symbols");
};

/**
 * 获取支持的交易对详情（code + name）
 */
export const getSupportedSymbolDetails = async () => {
  return await get("/v1/kline/symbols/details");
};

/**
 * 搜索交易对
 */
export const searchSymbols = async (keyword: string) => {
  return await get("/v1/kline/symbol/search", { params: { keyword } });
};

/**
 * 获取服务器时间
 */
export const getServerTime = async () => {
  return await get("/v1/kline/server-time");
};

/**
 * 时间跳转 - 跳转到指定时间点
 */
export const jumpToTime = async (params: {
  symbol: string;
  interval: string;
  exchange?: string;
  time: number;
  before?: number;
  after?: number;
  limit?: number;
}) => {
  return await post("/v1/kline/jump", params);
};

/**
 * 获取K线信号标注
 */
export const loadKLineSignals = async (params: {
  symbol: string;
  interval: string;
  exchange?: string;
  from: number;
  to: number;
  indicator?: string;
  robotId?: string;
  memberId?: string;
  accountId?: string;
  signalType?: string;
  limit?: number;
}) => {
  return await post("/v1/kline/signals", params);
};

/**
 * 从交易所导入历史K线到数据库（数据导入 - API导入）
 * accountId 可选：选择后使用该账户的交易所 API；不选则使用公开 API
 */
export const importKlineFromExchange = async (params: {
  exchange: string;
  symbol: string;
  interval: string;
  startTime: number;
  endTime: number;
  accountId?: string;
}) => {
  return await post("/v1/kline/import-from-exchange", params);
};

/**
 * 获取所有币种的最新K线Ticker
 */
export const getLatestTickers = async (params?: {
  interval?: string;
  limit?: number;
}) => {
  return await get("/v1/kline/latest-tickers", { params });
};

// ========== 类型定义 ==========

export interface KLineData {
  time: number;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
  quoteVolume?: number;
  tradeCount?: number;
}

export interface KLineHistoryResponse {
  code: number;
  message: string;
  data: {
    symbol: string;
    interval: string;
    klines: KLineData[];
    currentTime: number;
  };
  timestamp?: number;
}

export interface LatestTicker {
  symbol: string;
  interval: string;
  time: number;
  open: number;
  close: number;
  high: number;
  low: number;
  volume: number;
  changePercent?: number;
}

export interface KLineLoadResponse {
  code: number;
  message: string;
  data: {
    symbol: string;
    interval: string;
    direction: "forward" | "backward";
    data: KLineData[];
    hasMore: boolean;
    nextAnchorTime?: number;
  };
  timestamp?: number;
}

export interface IndicatorResponse {
  code: number;
  message: string;
  data: {
    indicatorType: string;
    data: Array<{
      time: number;
      value: number;
      [key: string]: any;
    }>;
  };
  timestamp?: number;
}

export interface KLineJumpResponse {
  code: number;
  message: string;
  data: {
    symbol: string;
    interval: string;
    targetTime: number;
    klines: KLineData[];
    currentTime: number;
    hasMoreBefore?: boolean;
    hasMoreAfter?: boolean;
  };
  timestamp?: number;
}

export interface KLineSignal {
  id: number;
  time: number;
  signalType: string | null; // 可能为null，需要从description中提取
  price: number | null; // 可能为null
  description: string;
  signalStrength?: number | null;
  signalSource?: string | null;
  robotId?: string | null;
  orderSn?: string | null;
  status?: string | null;
  entryType?: string | null;
  limitPrice?: number | null;
  extraParams?: string | null;
  marketTrend?: string | null;
}

export interface KLineSignalResponse {
  code: number;
  message: string;
  data: {
    symbol: string;
    interval: string;
    from: number;
    to: number;
    signals: KLineSignal[];
    total: number;
  };
  timestamp?: number;
}
