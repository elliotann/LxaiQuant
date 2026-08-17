import { get, post, put, del } from "./base";

// 获取持仓树
export const getPositionTree = async (params?: any) => {
  return await get("/trade-order/position-tree", { params });
};

// 关闭订单项
export const closeOrderItem = async (data: any) => {
  return await post("/trade-order/close-item", data);
};

// 更新订单项止盈止损
export const updateOrderItemGainLoss = async (id: string, data: any) => {
  return await put(`/trade-order/item/${id}/gain-loss`, data);
};

// 更新订单项
export const updateOrderItem = async (id: string, data: any) => {
  return await put(`/trade-order/item/${id}`, data);
};

// 获取订单列表
export const getOrderList = async (params?: any) => {
  return await get("/trade-order/list", { params });
};

// 调用OrderManagerController的分页查询订单列表接口
export const queryOrders = async (params?: any) => {
  return await get("/order/list", { params });
};

// 查询指定订单的订单项列表
export const listOrderItems = async (orderSn: string) => {
  return await get(`/order/${orderSn}/items`);
};

// 查询指定订单的平仓记录列表
export const listOrderCloses = async (orderSn: string) => {
  return await get(`/order/${orderSn}/closes`);
};

// 查询指定订单的平仓明细列表（带 orderItemSn 关联）
export const listOrderCloseItems = async (orderSn: string) => {
  return await get(`/order/${orderSn}/close-items`);
};

/** 机器人订单收益报表（按日/月聚合）- 专用报表 API */
export const getRobotOrderReport = async (params: {
  robotId: string;
  startTime?: number;
  endTime?: number;
  granularity?: "day" | "month";
}) => {
  return await get("/order/report/by-robot", { params });
};

// 创建订单
export const createOrder = async (data: any) => {
  return await post("/trade-order", data);
};

// 取消订单
export const cancelOrder = async (id: string) => {
  return await post(`/trade-order/${id}/cancel`);
};

// 手工开单（开仓）
export const manualOpenOrder = async (data: {
  accountId: string;
  robotId?: string;
  symbol: string;
  side: "LONG" | "SHORT" | "BUY" | "SELL";
  orderType: "MARKET" | "LIMIT";
  quantity: number;
  limitPrice?: number;
  entryPrice?: number;
  timeInForce?: string;
  leverage?: number;
  exchange?: string;
  requestId?: string;
  channel?: string;
  metadata?: Record<string, any>;
}) => {
  return await post("/manual-orders/open", data);
};

// 手工平仓
export const manualClosePosition = async (data: {
  accountId: string;
  robotId?: string;
  symbol: string;
  side: "LONG" | "SHORT";
  quantity?: number;
  orderType?: "MARKET" | "LIMIT";
  limitPrice?: number;
  requestId?: string;
  channel?: string;
  metadata?: Record<string, any>;
}) => {
  return await post("/manual-orders/close", data);
};

export const manualReversePosition = async (data: {
  accountId: string;
  robotId?: string;
  symbol: string;
  fromSide: "LONG" | "SHORT";
  toSide: "LONG" | "SHORT";
  quantity?: number;
  orderType?: "MARKET" | "LIMIT";
  limitPrice?: number;
  requestId?: string;
  channel?: string;
  metadata?: Record<string, any>;
}) => {
  return await post("/manual-orders/reverse", data);
};
