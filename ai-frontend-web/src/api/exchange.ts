import { api } from "./index";

export const exchangeApi = {
  // 账户管理
  getAccounts: () => api.get("/trading/accounts"),
  getAccount: (id: string) => api.get(`/trading/accounts/${id}`),
  createAccount: (data: any) => api.post("/trading/accounts", data),
  updateAccount: (id: string, data: any) =>
    api.put(`/trading/accounts/${id}`, data),
  deleteAccount: (id: string) => api.delete(`/trading/accounts/${id}`),

  // 连接管理
  connectAccount: (id: string) => api.post(`/trading/accounts/${id}/connect`),
  disconnectAccount: (id: string) =>
    api.post(`/trading/accounts/${id}/disconnect`),
  testConnection: (id: string) =>
    api.post(`/trading/accounts/${id}/test-connection`),
  testConnectionConfig: (config: any) =>
    api.post("/trading/accounts/test-connection-config", config),

  // 数据同步
  syncAccount: (id: string) => api.post(`/trading/accounts/${id}/sync`),
  getSyncStatus: (id: string) => api.get(`/trading/accounts/${id}/sync/status`),

  // 交易操作
  placeOrder: (accountId: string, orderData: any) =>
    api.post(`/trading/accounts/${accountId}/orders`, orderData),
  cancelOrder: (accountId: string, orderId: string, symbol?: string) =>
    api.delete(`/trading/accounts/${accountId}/orders/${orderId}`, {
      params: { symbol },
    }),
  cancelAllOrders: (accountId: string, symbol?: string) =>
    api.delete(`/trading/accounts/${accountId}/orders`, { params: { symbol } }),

  // 订单查询
  getOrders: (accountId: string, symbol?: string) =>
    api.get(`/trading/accounts/${accountId}/orders`, { params: { symbol } }),
  getOpenOrders: (accountId: string, symbol?: string) =>
    api.get(`/trading/accounts/${accountId}/orders/open`, {
      params: { symbol },
    }),
  getClosedOrders: (accountId: string, symbol?: string, limit?: number) =>
    api.get(`/trading/accounts/${accountId}/orders/closed`, {
      params: { symbol, limit },
    }),
  getOrder: (accountId: string, orderId: string) =>
    api.get(`/trading/accounts/${accountId}/orders/${orderId}`),

  // 账户数据
  getBalance: (accountId: string) =>
    api.get(`/trading/accounts/${accountId}/balance`),
  getPositions: (accountId: string) =>
    api.get(`/trading/accounts/${accountId}/positions`),
  getTrades: (accountId: string, symbol?: string, limit?: number, config?: any) =>
    api.get(`/trading/accounts/${accountId}/trades`, {
      ...(config || {}),
      params: { symbol, limit, ...(config?.params || {}) },
    }),

  // 市场数据
  getTicker: (accountId: string, symbol: string, config?: any) =>
    api.get(`/trading/accounts/${accountId}/ticker/${encodeURIComponent(symbol)}`, config),
  getTickers: (accountId: string, symbols?: string[], config?: any) =>
    api.get(`/trading/accounts/${accountId}/tickers`, {
      ...(config || {}),
      params: { symbols, ...(config?.params || {}) },
    }),
  getOrderBook: (accountId: string, symbol: string, limit?: number, config?: any) =>
    api.get(`/trading/accounts/${accountId}/orderbook/${encodeURIComponent(symbol)}`, {
      ...(config || {}),
      params: { limit, ...(config?.params || {}) },
    }),
  getOHLCV: (
    accountId: string,
    symbol: string,
    timeframe?: string,
    limit?: number,
    config?: any,
  ) =>
    api.get(`/trading/accounts/${accountId}/ohlcv/${encodeURIComponent(symbol)}`, {
      ...(config || {}),
      params: { timeframe, limit, ...(config?.params || {}) },
    }),

  // 交易对信息
  getMarkets: (accountId: string) =>
    api.get(`/trading/accounts/${accountId}/markets`),
  getExchangeInfo: (accountId: string) =>
    api.get(`/trading/accounts/${accountId}/info`),

  // 余额同步
  syncAccountBalance: (accountId: string) =>
    api.post(`/trading/accounts/${accountId}/sync-balance`),
  syncAllBalances: () => api.post("/trading/accounts/sync-all-balances"),

  // 批量操作
  getAllBalances: () => api.get("/trading/accounts/balances"),
  getAllPositions: () => api.get("/trading/accounts/positions"),
  getAllOpenOrders: () => api.get("/trading/accounts/orders/open"),

  // 系统状态
  getStatus: (accountId?: string) =>
    api.get("/trading/accounts/status", { params: { accountId } }),
  getConnectedExchanges: () => api.get("/trading/accounts/connected"),
};

export default exchangeApi;
