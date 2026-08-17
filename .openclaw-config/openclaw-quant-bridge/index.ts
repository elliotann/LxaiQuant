type QuantBridgeConfig = {
  baseUrl?: string;
  token?: string;
  defaultAccountId?: string;
  defaultSymbol?: string;
  defaultInterval?: string;
};

type OpenClawApi = {
  id: string;
  pluginConfig?: Record<string, unknown>;
  registerTool: (tool: any, opts?: { optional?: boolean }) => void;
};

const toStr = (v: unknown) => (typeof v === "string" ? v : v == null ? "" : String(v));

const readCfg = (api: OpenClawApi): QuantBridgeConfig => {
  const cfg = (api.pluginConfig || {}) as Record<string, unknown>;
  const envToken = typeof process !== "undefined" ? toStr((process as any).env?.OPENCLAW_BRIDGE_TOKEN) : "";
  return {
    baseUrl: toStr(cfg.baseUrl) || "http://127.0.0.1:8118",
    token: toStr(cfg.token) || envToken,
    defaultAccountId: toStr(cfg.defaultAccountId),
    defaultSymbol: toStr(cfg.defaultSymbol) || "BTC-USDT-SWAP",
    defaultInterval: toStr(cfg.defaultInterval) || "3m",
  };
};

const buildUrl = (baseUrl: string, path: string, query?: Record<string, string>) => {
  const base = baseUrl.replace(/\/+$/, "");
  const p = path.startsWith("/") ? path : `/${path}`;
  const url = new URL(base + p);
  if (query) {
    for (const [k, v] of Object.entries(query)) {
      if (v == null || v === "") continue;
      url.searchParams.set(k, v);
    }
  }
  return url.toString();
};

const httpJson = async (cfg: QuantBridgeConfig, init: RequestInit & { path: string; query?: Record<string, string> }) => {
  const url = buildUrl(cfg.baseUrl || "", init.path, init.query);
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
  };
  if (cfg.token) {
    headers["X-OpenClaw-Token"] = cfg.token;
  }
  const res = await fetch(url, {
    method: init.method,
    headers,
    body: init.body,
  });
  const text = await res.text().catch(() => "");
  if (!res.ok) {
    throw new Error(text || `HTTP ${res.status}`);
  }
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch (_) {
    return { raw: text };
  }
};

const asTextResult = (value: unknown) => {
  const text = typeof value === "string" ? value : JSON.stringify(value, null, 2);
  return { content: [{ type: "text", text }] };
};

const newRequestId = () => `${Date.now()}-${Math.random().toString(16).slice(2)}`;

export default function (api: OpenClawApi) {
  const cfg = readCfg(api);

  api.registerTool({
    name: "quant_health",
    description: "Check quant system bridge health",
    parameters: { type: "object", additionalProperties: false, properties: {} },
    async execute() {
      const result = await httpJson(cfg, { method: "GET", path: "/api/openclaw/health" });
      return asTextResult(result);
    },
  });

  api.registerTool({
    name: "quant_risk_status",
    description: "Get risk/status summary for an account or robot",
    parameters: {
      type: "object",
      additionalProperties: false,
      properties: {
        accountId: { type: "string" },
        robotId: { type: "string" },
      },
    },
    async execute(_id: string, params: any) {
      const result = await httpJson(cfg, {
        method: "GET",
        path: "/api/openclaw/risk/status",
        query: {
          accountId: toStr(params?.accountId),
          robotId: toStr(params?.robotId),
        },
      });
      return asTextResult(result);
    },
  });

  api.registerTool({
    name: "quant_list_accounts",
    description: "List trading accounts (for finding accountId / simulated accounts).",
    parameters: { type: "object", additionalProperties: false, properties: {} },
    async execute() {
      const result = await httpJson(cfg, { method: "GET", path: "/api/trading/accounts" });
      return asTextResult(result);
    },
  });

  api.registerTool({
    name: "quant_positions",
    description: "List current position orders for a symbol (raw position orders).",
    parameters: {
      type: "object",
      additionalProperties: false,
      properties: {
        accountId: { type: "string" },
        symbol: { type: "string" },
      },
      required: [],
    },
    async execute(_id: string, params: any) {
      const accountId = toStr(params?.accountId) || cfg.defaultAccountId;
      const symbol = toStr(params?.symbol) || cfg.defaultSymbol;
      if (!accountId) throw new Error("accountId 未提供，且插件未配置 defaultAccountId");
      if (!symbol) throw new Error("symbol 未提供，且插件未配置 defaultSymbol");
      const result = await httpJson(cfg, {
        method: "GET",
        path: "/api/openclaw/orders/positions",
        query: { accountId, symbol },
      });
      return asTextResult(result);
    },
  });

  api.registerTool({
    name: "quant_order_status",
    description: "Query order status by orderId or orderSn.",
    parameters: {
      type: "object",
      additionalProperties: false,
      properties: {
        orderId: { type: "string" },
        orderSn: { type: "string" },
      },
      required: [],
    },
    async execute(_id: string, params: any) {
      const orderId = toStr(params?.orderId);
      const orderSn = toStr(params?.orderSn);
      if (!orderId && !orderSn) throw new Error("orderId 或 orderSn 至少提供一个");
      const result = await httpJson(cfg, {
        method: "GET",
        path: "/api/openclaw/orders/order-status",
        query: { orderId, orderSn },
      });
      return asTextResult(result);
    },
  });

  api.registerTool({
    name: "quant_trade_plan_create",
    description: "Create a trade plan record based on a previewId and plan content.",
    parameters: {
      type: "object",
      additionalProperties: false,
      properties: {
        name: { type: "string" },
        description: { type: "string" },
        previewId: { type: "string" },
        planContent: { type: "object" },
        trace: { type: "object" },
      },
      required: ["previewId"],
    },
    async execute(_id: string, params: any) {
      const body = {
        name: toStr(params?.name),
        description: toStr(params?.description),
        previewId: toStr(params?.previewId),
        planContent: params?.planContent && typeof params.planContent === "object" ? params.planContent : undefined,
        trace: params?.trace && typeof params.trace === "object" ? params.trace : undefined,
      };
      const result = await httpJson(cfg, {
        method: "POST",
        path: "/api/openclaw/trade-plans",
        body: JSON.stringify(body),
      });
      return asTextResult(result);
    },
  });

  api.registerTool({
    name: "quant_trade_plan_get",
    description: "Get a previously created trade plan by planUuid.",
    parameters: {
      type: "object",
      additionalProperties: false,
      properties: {
        planUuid: { type: "string" },
      },
      required: ["planUuid"],
    },
    async execute(_id: string, params: any) {
      const planUuid = toStr(params?.planUuid);
      if (!planUuid) throw new Error("planUuid不能为空");
      const result = await httpJson(cfg, {
        method: "GET",
        path: `/api/openclaw/trade-plans/${encodeURIComponent(planUuid)}`,
      });
      return asTextResult(result);
    },
  });

  api.registerTool(
    {
      name: "quant_trade_plan_confirm",
      description: "Confirm a trade plan using planUuid and previewId.",
      parameters: {
        type: "object",
        additionalProperties: false,
        properties: {
          planUuid: { type: "string" },
          previewId: { type: "string" },
        },
        required: ["planUuid", "previewId"],
      },
      async execute(_id: string, params: any) {
        const planUuid = toStr(params?.planUuid);
        const previewId = toStr(params?.previewId);
        if (!planUuid) throw new Error("planUuid不能为空");
        if (!previewId) throw new Error("previewId不能为空");
        const result = await httpJson(cfg, {
          method: "POST",
          path: `/api/openclaw/trade-plans/${encodeURIComponent(planUuid)}/confirm`,
          body: JSON.stringify({ previewId }),
        });
        return asTextResult(result);
      },
    },
    { optional: true },
  );

  api.registerTool({
    name: "quant_get_signals",
    description: "Query recent signals for a symbol",
    parameters: {
      type: "object",
      additionalProperties: false,
      properties: {
        symbol: { type: "string" },
        interval: { type: "string" },
        indicatorType: { type: "string" },
        limit: { type: "number" },
      },
    },
    async execute(_id: string, params: any) {
      const symbol = toStr(params?.symbol) || cfg.defaultSymbol || "BTCUSDT";
      const interval = toStr(params?.interval) || cfg.defaultInterval || "3m";
      const limitNum = typeof params?.limit === "number" && params.limit > 0 ? Math.floor(params.limit) : 20;
      const pageSize = Math.min(200, Math.max(1, limitNum));
      const result = await httpJson(cfg, {
        method: "GET",
        path: "/api/openclaw/signals",
        query: {
          symbol,
          interval,
          indicatorType: toStr(params?.indicatorType),
          pageNumber: "1",
          pageSize: String(pageSize),
        },
      });
      return asTextResult(result);
    },
  });

  api.registerTool({
    name: "quant_latest_price",
    description: "Query latest price from local kline storage (OpenClaw bridge).",
    parameters: {
      type: "object",
      additionalProperties: false,
      properties: {
        symbol: { type: "string" },
        interval: { type: "string" },
      },
    },
    async execute(_id: string, params: any) {
      const symbol = toStr(params?.symbol) || cfg.defaultSymbol;
      const interval = toStr(params?.interval) || cfg.defaultInterval || "3m";
      if (!symbol) throw new Error("symbol 未提供，且插件未配置 defaultSymbol");
      const result = await httpJson(cfg, {
        method: "GET",
        path: "/api/openclaw/price/latest",
        query: { symbol, interval },
      });
      return asTextResult(result);
    },
  });

  api.registerTool(
    {
      name: "quant_open_order",
      description: "Preview a manual open order via the quant system. Use quant_open_order_confirm to execute.",
      parameters: {
        type: "object",
        additionalProperties: false,
        properties: {
          accountId: { type: "string" },
          robotId: { type: "string" },
          symbol: { type: "string" },
          side: { type: "string", enum: ["LONG", "SHORT", "BUY", "SELL"] },
          orderType: { type: "string", enum: ["MARKET", "LIMIT"] },
          quantity: { type: "number" },
          limitPrice: { type: "number" },
          timeInForce: { type: "string" },
          leverage: { type: "number" },
          requestId: { type: "string" },
          metadata: { type: "object" },
        },
        required: ["side", "orderType", "quantity"],
      },
      async execute(_id: string, params: any) {
        const accountId = toStr(params?.accountId) || cfg.defaultAccountId;
        const symbol = toStr(params?.symbol) || cfg.defaultSymbol;
        if (!accountId) {
          throw new Error("accountId 未提供，且插件未配置 defaultAccountId");
        }
        if (!symbol) {
          throw new Error("symbol 未提供，且插件未配置 defaultSymbol");
        }
        const body = {
          accountId,
          robotId: toStr(params?.robotId),
          symbol,
          side: toStr(params?.side),
          orderType: toStr(params?.orderType),
          quantity: params?.quantity,
          limitPrice: params?.limitPrice,
          timeInForce: toStr(params?.timeInForce),
          leverage: params?.leverage,
          requestId: toStr(params?.requestId) || newRequestId(),
          channel: "OPENCLAW",
          metadata: params?.metadata && typeof params.metadata === "object" ? params.metadata : undefined,
        };
        const result = await httpJson(cfg, {
          method: "POST",
          path: "/api/openclaw/orders/open/preview",
          body: JSON.stringify(body),
        });
        return asTextResult(result);
      },
    },
    { optional: true },
  );

  api.registerTool(
    {
      name: "quant_open_order_confirm",
      description: "Confirm and execute a previously previewed open order.",
      parameters: {
        type: "object",
        additionalProperties: false,
        properties: {
          previewId: { type: "string" },
        },
        required: ["previewId"],
      },
      async execute(_id: string, params: any) {
        const body = { previewId: toStr(params?.previewId) };
        const result = await httpJson(cfg, {
          method: "POST",
          path: "/api/openclaw/orders/open/confirm",
          body: JSON.stringify(body),
        });
        return asTextResult(result);
      },
    },
    { optional: true },
  );

  api.registerTool(
    {
      name: "quant_close_order",
      description: "Preview a manual close order via the quant system. Use quant_close_order_confirm to execute.",
      parameters: {
        type: "object",
        additionalProperties: false,
        properties: {
          accountId: { type: "string" },
          robotId: { type: "string" },
          symbol: { type: "string" },
          side: { type: "string", enum: ["LONG", "SHORT", "BUY", "SELL"] },
          quantity: { type: "number" },
          orderType: { type: "string", enum: ["MARKET", "LIMIT"] },
          limitPrice: { type: "number" },
          requestId: { type: "string" },
          metadata: { type: "object" },
        },
        required: ["side"],
      },
      async execute(_id: string, params: any) {
        const accountId = toStr(params?.accountId) || cfg.defaultAccountId;
        const symbol = toStr(params?.symbol) || cfg.defaultSymbol;
        if (!accountId) {
          throw new Error("accountId 未提供，且插件未配置 defaultAccountId");
        }
        if (!symbol) {
          throw new Error("symbol 未提供，且插件未配置 defaultSymbol");
        }
        const body = {
          accountId,
          robotId: toStr(params?.robotId),
          symbol,
          side: toStr(params?.side),
          quantity: params?.quantity,
          orderType: toStr(params?.orderType),
          limitPrice: params?.limitPrice,
          requestId: toStr(params?.requestId) || newRequestId(),
          channel: "OPENCLAW",
          metadata: params?.metadata && typeof params.metadata === "object" ? params.metadata : undefined,
        };
        const result = await httpJson(cfg, {
          method: "POST",
          path: "/api/openclaw/orders/close/preview",
          body: JSON.stringify(body),
        });
        return asTextResult(result);
      },
    },
    { optional: true },
  );

  api.registerTool(
    {
      name: "quant_close_order_confirm",
      description: "Confirm and execute a previously previewed close order.",
      parameters: {
        type: "object",
        additionalProperties: false,
        properties: {
          previewId: { type: "string" },
        },
        required: ["previewId"],
      },
      async execute(_id: string, params: any) {
        const body = { previewId: toStr(params?.previewId) };
        const result = await httpJson(cfg, {
          method: "POST",
          path: "/api/openclaw/orders/close/confirm",
          body: JSON.stringify(body),
        });
        return asTextResult(result);
      },
    },
    { optional: true },
  );
}
