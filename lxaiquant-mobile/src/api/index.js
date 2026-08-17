import axios from 'axios'
import { showToast } from 'vant'
import { DEFAULT_SERVER_URL } from '@/config'
import router from '@/router'
import { useUserStore } from '@/stores'

const http = axios.create({
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

export const getBaseUrl = () => {
  // 开发环境走 Vite 代理，无需 baseURL
  if (typeof import.meta !== 'undefined' && import.meta.env?.DEV) {
    return ''
  }
  const serverUrl = localStorage.getItem('serverUrl')?.trim()
  if (serverUrl) {
    return serverUrl.replace(/\/$/, '')
  }
  return DEFAULT_SERVER_URL
}

const ensureArray = (value) => Array.isArray(value) ? value : []

/** Global market calendar: API may return { list: [] } / { events: [] } etc. */
const unwrapCalendarList = (data) => {
  if (Array.isArray(data)) return data
  if (!data || typeof data !== 'object') return []
  const keys = ['items', 'list', 'events', 'calendar', 'records', 'rows', 'data']
  for (const k of keys) {
    const v = data[k]
    if (Array.isArray(v)) return v
  }
  return []
}

const parseNumericMetric = (val) => {
  if (val == null || val === '') return { text: '', num: null }
  if (typeof val === 'number') {
    return Number.isFinite(val) ? { text: String(val), num: val } : { text: '', num: null }
  }
  const text = String(val).trim()
  if (!text) return { text: '', num: null }
  const match = text.replace(/,/g, '').match(/-?\d+(\.\d+)?/)
  const num = match ? Number(match[0]) : null
  return { text, num: Number.isFinite(num) ? num : null }
}

// 事件 → 黄金影响极性：{ bullish: '实际高于预期->利多', bearish: ... }
// 命中的关键词按从上到下优先级
const GOLD_IMPACT_RULES = [
  { kw: /unemploy|jobless|initial\s*claims|失业|申请失业/, higherIs: 'bullish' },
  { kw: /cpi|inflation|ppi|pce|消费者物价|生产者物价|通胀|通膨/, higherIs: 'bullish' },
  { kw: /nonfarm|payroll|non[- ]?farm|非农|就业/, higherIs: 'bearish' },
  { kw: /gdp|国内生产总值/, higherIs: 'bearish' },
  { kw: /retail\s*sales|零售销售/, higherIs: 'bearish' },
  { kw: /pmi|ism|制造业|采购经理/, higherIs: 'bearish' },
  { kw: /industrial\s*production|工业产出|工业生产/, higherIs: 'bearish' },
  { kw: /durable\s*goods|耐用品/, higherIs: 'bearish' },
  { kw: /consumer\s*confidence|消费者信心/, higherIs: 'bearish' },
  { kw: /trade\s*balance|贸易差额|贸易逆差/, higherIs: 'bearish' },
  { kw: /housing|home\s*sales|住房/, higherIs: 'bearish' },
  { kw: /dollar\s*index|dxy|美元指数/, higherIs: 'bearish' },
  { kw: /fed\s*funds|interest\s*rate|rate\s*decision|利率决议|基准利率|federal\s*funds/, higherIs: 'bearish' }
]

const decideGoldImpact = (titleRaw, surprise) => {
  if (!surprise || surprise === 'inline') return null
  const t = String(titleRaw || '').toLowerCase()
  if (!t) return null
  const rule = GOLD_IMPACT_RULES.find(r => r.kw.test(t))
  if (!rule) return null
  const opposite = rule.higherIs === 'bullish' ? 'bearish' : 'bullish'
  return surprise === 'higher' ? rule.higherIs : opposite
}

const normalizeCalendarEvent = (ev) => {
  if (!ev || typeof ev !== 'object') return null
  const title =
    ev.title ??
    ev.event_title ??
    ev.event_name ??
    ev.name ??
    ev.event ??
    ev.subject ??
    ev.indicator ??
    ev.indicator_name ??
    ev.description ??
    ev.holiday_name ??
    ''
  const titleEn =
    ev.title_en ??
    ev.name_en ??
    ev.event_name_en ??
    ev.event_title_en ??
    ev.indicator_en ??
    ''
  const titleZh =
    ev.title_zh ??
    ev.name_zh ??
    ev.event_name_zh ??
    ev.event_title_zh ??
    ''
  const time =
    ev.time ??
    ev.event_time ??
    ev.datetime ??
    ev.date_time ??
    ev.date ??
    ev.scheduled ??
    '--'
  const country =
    ev.country ??
    ev.region ??
    ev.currency ??
    ev.currency_code ??
    ev.ccy ??
    ''
  const id = ev.id ?? ev.event_id ?? `${String(time)}-${String(title)}-${String(country)}`
  const impact = String(ev.impact ?? ev.importance ?? ev.level ?? '').toLowerCase()

  const actualMetric = parseNumericMetric(
    ev.actual ?? ev.actual_value ?? ev.actualValue ?? ev.value ?? ev.result
  )
  const forecastMetric = parseNumericMetric(
    ev.forecast ?? ev.forecast_value ?? ev.forecastValue ?? ev.expected ?? ev.consensus ?? ev.estimate
  )
  const previousMetric = parseNumericMetric(
    ev.previous ?? ev.previous_value ?? ev.previousValue ?? ev.prior ?? ev.last
  )

  let surprise = null // 'higher' | 'lower' | 'inline'
  if (actualMetric.num != null && forecastMetric.num != null) {
    if (actualMetric.num > forecastMetric.num) surprise = 'higher'
    else if (actualMetric.num < forecastMetric.num) surprise = 'lower'
    else surprise = 'inline'
  }

  const goldImpact = decideGoldImpact(title, surprise) // 'bullish' | 'bearish' | null

  return {
    ...ev,
    id,
    time: String(time),
    country: String(country),
    title: String(title || '--'),
    title_en: String(titleEn || ''),
    title_zh: String(titleZh || ''),
    impact,
    actual: actualMetric.text,
    forecast: forecastMetric.text,
    previous: previousMetric.text,
    actualNum: actualMetric.num,
    forecastNum: forecastMetric.num,
    previousNum: previousMetric.num,
    surprise,
    goldImpact
  }
}

/** Fear & Greed: PC/alternative.me often returns { value, classification } not fear_greed. */
const normalizeGlobalSentiment = (raw) => {
  if (raw == null) return null
  let d = raw
  if (typeof raw === 'string') {
    try {
      d = JSON.parse(raw)
    } catch {
      return null
    }
  }
  if (typeof d !== 'object') return null
  if (d.data != null && typeof d.data === 'object' && !Array.isArray(d.data)) {
    const nested = d.data
    if (
      nested.value != null ||
      nested.fear_greed != null ||
      nested.classification != null ||
      (typeof nested.fear_greed === 'object' && nested.fear_greed !== null)
    ) {
      d = nested
    }
  }
  const inner =
    typeof d.fear_greed === 'object' && d.fear_greed !== null && !Array.isArray(d.fear_greed)
      ? d.fear_greed
      : d
  const fromNumber = typeof d.fear_greed === 'number' ? d.fear_greed : NaN
  const n = Number(
    inner.value ??
      inner.fear_greed ??
      (Number.isFinite(fromNumber) ? fromNumber : NaN) ??
      d.value ??
      NaN
  )
  if (!Number.isFinite(n)) return null
  return {
    fear_greed: Math.round(Math.max(0, Math.min(100, n))),
    classification: String(
      inner.classification ?? d.classification ?? inner.label ?? ''
    ).trim(),
    source: String(inner.source ?? d.source ?? ''),
    timestamp: inner.timestamp ?? d.timestamp
  }
}

const unwrapItems = (data, key = 'items') => {
  if (Array.isArray(data)) return data
  if (Array.isArray(data?.[key])) return data[key]
  return []
}

/** 后端 NotificationLog → H5 前端通知对象字段映射（camelCase → snake_case） */
const normalizeNotification = (item) => {
  if (!item || typeof item !== 'object') return item
  return {
    id: item.id,
    title: item.title,
    content: item.content,
    message: item.content,
    type: item.type,
    event_type: item.type,
    is_read: item.isRead,
    read: item.isRead,
    created_at: item.sentAt || item.createTime,
    channel: item.channel,
    status: item.status,
    strategy_id: null
  }
}

const normalizeStrategy = (raw = {}) => {
  const tradingConfig = raw?.trading_config && typeof raw.trading_config === 'object' ? raw.trading_config : {}
  const indicatorConfig = raw?.indicator_config && typeof raw.indicator_config === 'object' ? raw.indicator_config : {}
  const exchangeConfig = raw?.exchange_config && typeof raw.exchange_config === 'object' ? raw.exchange_config : {}
  const notificationConfig = raw?.notification_config && typeof raw.notification_config === 'object' ? raw.notification_config : {}

  const name = raw.name || raw.strategy_name || raw.group_base_name || (raw.id ? `策略 #${raw.id}` : '未命名策略')
  const indicatorName = raw.indicator_name ||
    indicatorConfig.indicator_name ||
    indicatorConfig.name ||
    indicatorConfig.display_name ||
    indicatorConfig.indicator ||
    tradingConfig.bot_name ||
    ''

  const performance = raw.performance && typeof raw.performance === 'object'
    ? raw.performance
    : {
        total_pnl: Number(raw.total_pnl || raw.total_profit || raw.pnl || 0),
        win_rate: Number(raw.win_rate || 0),
        total_trades: Number(raw.total_trades || 0),
        profit_factor: Number(raw.profit_factor || 0)
      }

  return {
    ...raw,
    name,
    status: (raw.status || 'stopped').toLowerCase(),
    strategy_name: raw.strategy_name || name,
    type: raw.type || raw.strategy_type || '',
    symbol: raw.symbol || tradingConfig.symbol || '',
    timeframe: raw.timeframe || tradingConfig.timeframe || '',
    indicator_name: indicatorName,
    indicator: {
      ...(raw.indicator || {}),
      name: raw?.indicator?.name || indicatorName
    },
    trading_config: {
      ...tradingConfig,
      symbol: tradingConfig.symbol || raw.symbol || '',
      timeframe: tradingConfig.timeframe || raw.timeframe || '',
      initial_capital: tradingConfig.initial_capital || raw.initial_capital || 0,
      leverage: tradingConfig.leverage || raw.leverage || 1,
      market_type: tradingConfig.market_type || raw.market_type || ''
    },
    exchange_config: exchangeConfig,
    notification_config: notificationConfig,
    performance
  }
}

/** 登录/注册等「主动提交凭证」接口的 401 不应整页踢回登录（例如密码错误） */
const isAuthCredentialRequest = (url) =>
  /\/api\/auth\/(login|register|send-code|reset-password)(?:\?|$)/i.test(String(url || ''))

/** 重定向锁，防止并发 401 重复跳转 */
let _redirectingToLogin = false

function clearAuthSession() {
  try {
    localStorage.removeItem('token')
    useUserStore().logout()
  } catch (_) {
    localStorage.removeItem('token')
  }
}

/** 会话失效：清状态并回登录（已在登录页则只清状态） */
function redirectToLoginIfNeeded(requestUrl) {
  if (isAuthCredentialRequest(requestUrl)) {
    clearAuthSession()
    return
  }
  // 已跳转或跳转中则忽略，避免并发问题
  if (_redirectingToLogin) return
  _redirectingToLogin = true

  clearAuthSession()

  try {
    if (router.currentRoute.value.path === '/login') return
    const full = router.currentRoute.value.fullPath || '/home'
    // 用 setTimeout 推迟到下一个微任务，避免在 axios 拦截器链中产生导航冲突
    setTimeout(() => {
      router.replace({ path: '/login', query: { redirect: full } }).catch(() => {
        // router.replace 失败时降级为硬跳转
        window.location.href = '/login'
      })
    }, 0)
  } catch (_) {
    /* 兜底：硬跳转 */
    window.location.href = '/login'
  }
}

/** HTTP 200 但业务体表示需重新登录 */
function isSessionExpiredBusinessResponse(res) {
  if (!res || typeof res !== 'object') return false
  const code = res.code
  const msg = String(res.msg || res.message || '')
  if (code === 401) return true
  if (code === -1 || code === 403) {
    return /未登录|请重新登录|请登录|登录失效|登录过期|token|Token|会话|过期|失效|鉴权|unauthorized|invalid\s*token|挤掉|elsewhere|session/i.test(
      msg
    )
  }
  return false
}

/** 检测 JWT token 是否已过期（仅对 JWT 格式 token 生效） */
function isJwtTokenExpired(token) {
  try {
    const parts = token.split('.')
    if (parts.length !== 3) return false // 非 JWT 格式，跳过
    const payload = JSON.parse(atob(parts[1]))
    return payload.exp && payload.exp * 1000 < Date.now()
  } catch (_) {
    return false // 解析失败，不拦截
  }
}

http.interceptors.request.use(
  (config) => {
    config.baseURL = getBaseUrl()

    const token = localStorage.getItem('token')
    if (token) {
      // 主动检测 JWT 是否过期，避免无效请求
      if (isJwtTokenExpired(token)) {
        redirectToLoginIfNeeded(config.url)
        return Promise.reject(new Error('登录已过期'))
      }
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

http.interceptors.response.use(
  (response) => {
    const res = response.data
    if (response.config?.raw) {
      return res
    }
    if (res?.code === 1 || res?.code === 200 || res?.success) {
      return res
    }
    if (response.status === 200 && (res?.code === undefined || res?.code === null) && res?.success !== false) {
      return { code: 1, data: res }
    }
    const reqUrl = String(response.config?.url || '')
    if (isSessionExpiredBusinessResponse(res) && !isAuthCredentialRequest(reqUrl)) {
      redirectToLoginIfNeeded(reqUrl)
    }
    showToast({
      message: res?.msg || res?.message || '请求失败',
      type: 'fail'
    })
    return Promise.reject(new Error(res?.msg || res?.message || '请求失败'))
  },
  (error) => {
    let message = '网络错误'
    if (error.response) {
      switch (error.response.status) {
        case 401:
          message = '未授权，请重新登录'
          redirectToLoginIfNeeded(error.config?.url)
          break
        case 403:
          message = '拒绝访问'
          break
        case 404:
          message = '请求地址不存在'
          break
        case 500:
          message = '服务器错误'
          break
        default:
          message = error.response.data?.message || error.response.data?.msg || '请求失败'
      }
    } else if (error.message?.includes('timeout')) {
      message = '请求超时'
    } else if (error.message?.includes('Network Error')) {
      message = '网络连接失败，请检查服务器地址'
    }

    showToast({ message, type: 'fail' })
    return Promise.reject(error)
  }
)

export const authApi = {
  login: (data) => http.post('/api/auth/login', data),
  register: (data) => http.post('/api/auth/register', data),
  sendCode: (data) => http.post('/api/auth/send-email-code', data),
  resetPassword: (data) => http.post('/api/auth/reset-password', data),
  getInfo: () => http.get('/api/auth/me'),
  logout: () => http.post('/api/auth/logout'),
  changePassword: (data) => http.post('/api/auth/change-password', data)
}

export const dashboardApi = {
  getSummary: async () => {
    const res = await http.get('/api/trading/summary')
    return {
      ...res,
      data: res.data || {}
    }
  },
  getPendingOrders: async (params = {}) => {
    const res = await http.get('/api/dashboard/pendingOrders', { params })
    return {
      ...res,
      data: res.data || { items: [], total: 0 }
    }
  }
}

export const credentialsApi = {
  /** 获取凭证列表（字段映射：exchange→exchange_id, apiKey→api_key_hint, simulated→enable_demo_trading） */
  list: async () => {
    const res = await http.get('/api/trading/accounts')
    const raw = Array.isArray(res?.data) ? res.data : []
    const items = raw.map((item) => ({
      id: item.id,
      name: item.name,
      exchange_id: item.exchange,
      api_key_hint: item.apiKey ? `${item.apiKey.slice(0, 6)}****` : '',
      enable_demo_trading: !!item.simulated,
      created_at: item.createdAt
    }))
    return { ...res, data: items }
  },
  /** 获取单个凭证 */
  get: async (id) => {
    const res = await http.get(`/api/trading/accounts/${id}`)
    return { ...res, data: res?.data || null }
  },
  /** 创建凭证（字段映射与 PC 端一致） */
  create: async (data) => {
    // H5 exchange key → 后端枚举名映射
    const EXCHANGE_MAP = {
      gate: 'GATEIO',
      coinbaseexchange: 'COINBASE',
      htx: 'HUOBI'
    }
    const exchange = EXCHANGE_MAP[data.exchange_id] || data.exchange_id.toUpperCase()
    const res = await http.post('/api/trading/accounts', {
      name: data.name,
      exchange: exchange,
      apiKey: data.api_key,
      apiSecret: data.secret_key,
      passphrase: data.passphrase || '',
      simulated: !!data.enable_demo_trading
    }, {
      headers: { 'X-Requested-With': 'XMLHttpRequest' }
    })
    return res
  },
  /** 删除凭证 */
  delete: (id) => http.delete(`/api/trading/accounts/${id}`),
  /** 获取出口 IP（新后端可能不支持） */
  getEgressIp: async () => ({ data: {} })
}

export const strategyApi = {
  getTemplates: async (params = {}) => {
    const res = await http.get('/api/templates', { params })
    return {
      ...res,
      data: ensureArray(res.data)
    }
  },
  getTemplate: async (key) => {
    const res = await http.get(`/api/templates/${key}`)
    return {
      ...res,
      data: res.data || null
    }
  },
  create: (payload) => http.post('/api/strategies/create', payload),
  batchCreate: (payload) => http.post('/api/strategies/batch-create', payload),
  update: (id, payload) => http.put('/api/strategies/update', { id, ...payload }),
  delete: (id) => http.delete('/api/strategies/delete', { params: { id } }),
  aiGenerate: (payload) => http.post('/api/strategies/ai-generate', payload, { raw: true, timeout: 180000 }),
  getList: async () => {
    const res = await http.get('/api/strategies')
    return {
      ...res,
      data: ensureArray(res.data?.strategies).map(normalizeStrategy)
    }
  },
  getDetail: async (id) => {
    const res = await http.get('/api/strategies/detail', {
      params: { id }
    })
    return {
      ...res,
      data: res.data ? normalizeStrategy(res.data) : null
    }
  },
  start: (id) => http.post('/api/strategies/start', null, {
    params: { id }
  }),
  stop: (id) => http.post('/api/strategies/stop', null, {
    params: { id }
  }),
  getTrades: async (id, limit = 50) => {
    const res = await http.get('/api/strategies/trades', {
      params: { id, limit }
    })
    return {
      ...res,
      data: unwrapItems(res.data, 'trades')
    }
  },
  getPositions: async (id) => {
    const res = await http.get('/api/strategies/positions', {
      params: { id }
    })
    return {
      ...res,
      data: unwrapItems(res.data, 'positions')
    }
  },
  getEquityCurve: async (id) => {
    const res = await http.get('/api/strategies/equityCurve', {
      params: { id }
    })
    return {
      ...res,
      data: ensureArray(res.data)
    }
  },
  getPerformance: async (id) => {
    const res = await http.get('/api/strategies/performance', {
      params: { id }
    })
    return {
      ...res,
      data: res.data || {}
    }
  },
  getLogs: async (id, limit = 100) => {
    const res = await http.get('/api/strategies/logs', {
      params: { id, limit }
    })
    return {
      ...res,
      data: unwrapItems(res.data, 'logs')
    }
  },
  testConnection: (data) => http.post('/api/strategies/test-connection', data),
  getNotifications: async (params = {}) => {
    const res = await http.get('/api/notification/logs', {
      params: { page: 1, size: params.limit || 100 }
    })
    const page = res?.data || {}
    const items = Array.isArray(page?.records) ? page.records.map(normalizeNotification) : []
    return {
      ...res,
      data: items,
      total: page?.total ?? 0
    }
  },
  getUnreadNotificationCount: async () => {
    const res = await http.get('/api/notification/logs/unread-count')
    return { ...res, data: res?.data ?? 0 }
  },
  markNotificationRead: (id) => http.post(`/api/notification/logs/${id}/read`),
  markAllNotificationsRead: () => http.post('/api/notification/logs/read-all'),
  clearNotifications: () => Promise.resolve({ success: true, data: null })
}

/** 交易机器人 API：将 TradingBot 数据归一化为 UI 兼容格式 */
const normalizeTradingBot = (raw = {}) => {
  const statistics = (() => {
    if (!raw.statistics) return {}
    if (typeof raw.statistics === 'string') {
      try { return JSON.parse(raw.statistics) } catch { return {} }
    }
    return raw.statistics
  })()

  // 解析 configuration JSON，包含完整的交易参数配置
  const config = (() => {
    if (!raw.configuration) return {}
    if (typeof raw.configuration === 'string') {
      try { return JSON.parse(raw.configuration) } catch { return {} }
    }
    return raw.configuration
  })()

  return {
    id: raw.botId,
    name: raw.botName || raw.name || `机器人 #${raw.botId}`,
    symbol: raw.tradingPair || '',
    status: (raw.status || 'stopped').toLowerCase(),
    bot_id: raw.botId,
    createdBy: raw.createdBy || '',
    trading_config: {
      initial_capital: raw.allocatedCapital || config.initial_capital || 0,
      symbol: raw.tradingPair || config.symbol || '',
      timeframe: raw.timeframe || config.timeframe || '',
      market_type: config.market_type || config.marketType || '',
      leverage: config.leverage || 0,
      trade_direction: config.trade_direction || config.tradeDirection || '',
      order_mode: config.order_mode || config.orderMode || '',
      bot_params: config.bot_params || config.botParams || {},
      indicator_config: config.indicator_config || config.indicatorConfig || {},
      execution_mode: config.execution_mode || '',
      entry_pct: config.entry_pct,
      stop_loss_pct: config.stop_loss_pct,
      take_profit_pct: config.take_profit_pct,
      trailing_enabled: config.trailing_enabled,
      trailing_stop_pct: config.trailing_stop_pct,
      trailing_activation_pct: config.trailing_activation_pct,
      commission: config.commission,
      slippage: config.slippage,
      enable_ai_filter: config.enable_ai_filter,
      indicator_params: config.indicator_params || {}
    },
    bot_display: {
      strategy_params: config.strategy_params || config.strategyParams || [],
      risk_params: config.risk_params || config.riskParams || [],
      capital_label_key: config.capital_label_key || config.capitalLabelKey || 'allocatedCapital',
      indicators: config.indicators || []
    },
    exchange_config: {
      exchange_id: config.exchange_id || config.exchangeId || '',
      credential_id: config.credential_id || config.credentialId || ''
    },
    currentCapital: raw.currentCapital || 0,
    allocatedCapital: raw.allocatedCapital || 0,
    performance: {
      total_pnl: Number(statistics.total_pnl || statistics.realized_pnl || 0),
      unrealized_pnl: Number(statistics.unrealized_pnl || 0),
      win_rate: Number(statistics.win_rate || 0),
      total_trades: Number(statistics.total_trades || statistics.trade_count || 0),
      profit_factor: Number(statistics.profit_factor || 0)
    },
    unrealized_pnl: Number(statistics.unrealized_pnl || 0),
    indicator_name: raw.indicatorName || '',
    strategy_mode: raw.strategyMode || raw.strategy_mode || '',
    timeframe: raw.timeframe || '',
    strategy_id: raw.strategyId,
    startTime: raw.startTime,
    maxDrawdown: raw.maxDrawdown || statistics.max_drawdown || 0,
    _source: raw.remark === 'purchased' ? 'purchased' : undefined,
    _raw: raw
  }
}

/** 机器人收益报表（按日/月聚合），对应 PC 前端 getRobotOrderReport */
export const getRobotOrderReport = async (params = {}) => {
  return await http.get('/api/order/report/by-robot', { params })
}

/** 机器人详情页 API（兼容 strategy 页面接口，调用机器人相关后端端点） */
export const tradingBotDetailApi = {
  getDetail: async (id) => {
    const res = await tradingBotApi.getDetail(id)
    return { ...res, data: res.data || null }
  },
  getTrades: async (id, limit = 30) => {
    const res = await orderApi.list({ robotId: id, pageSize: limit })
    return { ...res, data: Array.isArray(res?.data) ? res.data : [] }
  },
  getPerformance: async (id) => {
    // 调用收益报表 API 获取净利润等数据
    const res = await getRobotOrderReport({ robotId: id })
    // 报表返回 { data: { netProfit, totalIncome, totalCharge, orderCount, equityCurve } }
    return { ...res, data: res.data || {} }
  },
  getEquityCurve: async (id) => {
    const res = await http.get('/api/robot/equity/latest', { params: { robotIds: id } })
    return { ...res, data: ensureArray(res.data) }
  },
  getLogs: async () => {
    return { data: [] }
  },
  start: async (id) => tradingBotApi.start(id),
  stop: async (id) => tradingBotApi.stop(id),
  delete: async (id) => tradingBotApi.delete(id)
}

export const tradingBotApi = {
  /** 获取机器人列表 */
  getList: async (params = {}) => {
    const res = await http.get('/api/trading-bots', { params: { page: 1, size: 1000, ...params } })
    const records = Array.isArray(res?.data?.records) ? res.data.records : ensureArray(res.data)
    return {
      ...res,
      data: records.map(normalizeTradingBot)
    }
  },
  /** 获取机器人详情 */
  getDetail: async (id) => {
    const res = await http.get(`/api/trading-bots/${id}`)
    return {
      ...res,
      data: res.data ? normalizeTradingBot(res.data) : null
    }
  },
  /** 创建机器人 */
  create: (data) => http.post('/api/trading-bots', data),
  /** 更新机器人 */
  update: (id, data) => http.put(`/api/trading-bots/${id}`, data),
  /** 启动机器人 */
  start: (id) => http.post(`/api/trading-bots/${id}/start`),
  /** 停止机器人 */
  stop: (id) => http.post(`/api/trading-bots/${id}/stop`),
  /** 删除机器人 */
  delete: (id) => http.delete(`/api/trading-bots/${id}`),
  /** 查询机器人某分组的所有参数 */
  getBotParameters: (botId, group) => http.get(`/api/trading-bots/${botId}/parameters/${group}`),
  /** 保存机器人某分组的所有参数（全量替换） */
  saveBotParameters: (botId, group, params) => http.put(`/api/trading-bots/${botId}/parameters/${group}`, params)
}

export const quickTradeApi = {
  getBalance: async (credentialId, marketType = 'spot') => {
    const res = await http.get('/api/quick-trade/balance', {
      params: {
        credential_id: credentialId,
        market_type: marketType
      }
    })
    return {
      ...res,
      data: res.data || { available: 0, total: 0, currency: 'USDT' }
    }
  },
  getPosition: async ({ credentialId, symbol, marketType = 'spot' }) => {
    const res = await http.get('/api/quick-trade/position', {
      params: {
        credential_id: credentialId,
        symbol,
        market_type: marketType
      }
    })
    return {
      ...res,
      data: unwrapItems(res.data, 'positions')
    }
  },
  placeOrder: (payload) => http.post('/api/quick-trade/place-order', payload),
  closePosition: (payload) => http.post('/api/quick-trade/close-position', payload),
  getHistory: async (params = {}) => {
    const res = await http.get('/api/quick-trade/history', { params })
    return {
      ...res,
      data: unwrapItems(res.data, 'trades')
    }
  }
}

export const aiAnalysisApi = {
  analyze: (payload) => http.post('/api/fast-analysis/analyze', payload, { timeout: 300000 }),
  getHistory: async (params = {}) => {
    const res = await http.get('/api/fast-analysis/history', { params })
    return {
      ...res,
      data: unwrapItems(res.data)
    }
  },
  getAllHistory: async (params = {}) => {
    const res = await http.get('/api/fast-analysis/history/all', { params })
    return {
      ...res,
      data: {
        list: ensureArray(res.data?.list),
        total: Number(res.data?.total || 0),
        page: Number(res.data?.page || 1),
        pagesize: Number(res.data?.pagesize || 20)
      }
    }
  },
  deleteHistory: (memoryId) => http.delete(`/api/fast-analysis/history/${memoryId}`),
  getPerformance: async (params = {}) => {
    const res = await http.get('/api/fast-analysis/performance', { params })
    return {
      ...res,
      data: res.data || {}
    }
  },
  submitFeedback: (payload) => http.post('/api/fast-analysis/feedback', payload),
  getSimilarPatterns: async (params = {}) => {
    const res = await http.get('/api/fast-analysis/similar-patterns', { params })
    return {
      ...res,
      data: res.data || {}
    }
  }
}

export const marketApi = {
  getIndicators: async (params = {}) => {
    const res = await http.get('/api/community/indicators', { params })
    return {
      ...res,
      data: {
        items: ensureArray(res.data?.items),
        total: Number(res.data?.total || 0),
        page: Number(res.data?.page || 1),
        page_size: Number(res.data?.page_size || 12)
      }
    }
  },
  getIndicator: async (id) => {
    const res = await http.get(`/api/community/indicators/${id}`)
    return {
      ...res,
      data: res.data || null
    }
  },
  purchase: (id) => http.post(`/api/community/indicators/${id}/purchase`),
  syncIndicator: (id) => http.post(`/api/community/indicators/${id}/sync`),
  getMyPurchases: async (params = {}) => {
    const res = await http.get('/api/community/my-purchases', { params })
    return {
      ...res,
      data: {
        items: ensureArray(res.data?.items),
        total: Number(res.data?.total || 0)
      }
    }
  },
  getComments: async (id, params = {}) => {
    const res = await http.get(`/api/community/indicators/${id}/comments`, { params })
    return {
      ...res,
      data: {
        items: ensureArray(res.data?.items),
        total: Number(res.data?.total || 0)
      }
    }
  },
  getIndicatorPerformance: async (id) => {
    const res = await http.get(`/api/community/indicators/${id}/performance`)
    return {
      ...res,
      data: res.data || {}
    }
  }
}

/**
 * 社区市场 API（替代旧的指标市场 /api/community/indicators）
 * 后端：CommunityMarketController -> /api/community-market
 */
export const botMarketApi = {
  /** 获取市场列表 */
  getListings: async (params = {}) => {
    // 参数映射：pricing_type -> pricingType, sort_by -> sortBy, page_size -> pageSize
    const backendParams = {
      page: params.page || 1,
      pageSize: params.pageSize || params.page_size || 12,
      keyword: params.keyword || undefined,
      pricingType: params.pricingType || params.pricing_type || undefined,
      sortBy: params.sortBy || params.sort_by || undefined
    }
    // 排序值映射：score/hot -> 后端缺省（按创建时间）
    if (backendParams.sortBy === 'score' || backendParams.sortBy === 'hot') {
      backendParams.sortBy = undefined
    }
    const res = await http.get('/api/community-market/listings', { params: backendParams })
    return {
      ...res,
      data: res.data || { items: [], total: 0 }
    }
  },
  /** 获取详情（含 performance + comments） */
  getListingDetail: async (id) => {
    const res = await http.get(`/api/community-market/listings/${id}`)
    return {
      ...res,
      data: res.data || null
    }
  },
  /** 获取实盘表现 */
  getPerformance: async (id) => {
    const res = await http.get(`/api/community-market/listings/${id}/performance`)
    return {
      ...res,
      data: res.data || {}
    }
  },
  /** 获取评论列表 */
  getComments: async (id, page = 1, pageSize = 10) => {
    const res = await http.get(`/api/community-market/listings/${id}/comments`, {
      params: { page, pageSize }
    })
    return {
      ...res,
      data: Array.isArray(res.data) ? res.data : []
    }
  },
  /** 购买 */
  purchaseBot: (id) => http.post(`/api/community-market/listings/${id}/purchase`),
  /** 同步更新 */
  syncBotUpdate: (id) => http.post(`/api/community-market/listings/${id}/sync`),
  /** 我的已购 */
  getMyPurchases: async () => {
    const res = await http.get('/api/community-market/my-purchases')
    return {
      ...res,
      data: Array.isArray(res.data) ? res.data : []
    }
  }
}

export const watchlistApi = {
  getList: async () => {
    const res = await http.get('/api/user/favorites')
    return {
      ...res,
      data: ensureArray(res.data).map((item) => ({
        id: item.id,
        symbolId: item.symbolId || item.symbol?.id,
        market: item.symbol?.market || '',
        symbol: item.symbol?.symbol || '',
        name: item.symbol?.name || item.symbol?.symbol || '',
        exchange: item.symbol?.exchange || ''
      }))
    }
  },
  add: (symbolId) => http.post(`/api/user/favorites/${symbolId}`),
  remove: (symbolId) => http.delete(`/api/user/favorites/${symbolId}`),
  search: async (params) => {
    const res = await http.get('/api/symbols', { params: { market: params.market, keyword: params.keyword } })
    return {
      ...res,
      data: ensureArray(res.data)
    }
  },
  getHot: async (params) => {
    const res = await http.get('/api/symbols', { params: { market: params.market, isHot: true } })
    return {
      ...res,
      data: ensureArray(res.data)
    }
  },
  // TODO: 后端接口暂未实现，后续补全
  // getPrices: async (list) => {
  //   const res = await http.get('/api/market/watchlist/prices', {
  //     params: { watchlist: JSON.stringify(list || []) }
  //   })
  //   return { ...res, data: ensureArray(res.data) }
  // },
  getPrices: async () => Promise.resolve({ data: [], code: 200 })
}

/** PC 端 interval 格式映射 */
const toBackendInterval = (tf) => {
  const map = {
    '1m': 'OKXMIN1', '3m': 'OKXMIN3', '5m': 'OKXMIN5',
    '15m': 'OKXMIN15', '30m': 'OKXMIN30',
    '1h': 'OKXMIN60', '2h': 'OKX2HOUR', '4h': 'OKX4HOUR',
    '1d': 'OKX1D', '1w': 'OKX1W'
  }
  return map[tf] || tf
}

export const klineApi = {
  /** 跳转到指定时间加载 K 线（与 PC 端市场行情一致） */
  jumpToTime: async ({ symbol, interval = '1h', exchange = 'GATEIO', time, before = 200, after = 200, limit = 400 } = {}) => {
    const res = await http.post('/api/v1/kline/jump', {
      symbol,
      interval: toBackendInterval(interval),
      exchange,
      time,
      before,
      after,
      limit
    })
    return {
      ...res,
      data: ensureArray(res?.data?.klines || res?.data || [])
    }
  },
  getPrice: async ({ market = 'Crypto', symbol } = {}) => {
    const res = await http.get('/api/indicator/price', { params: { market, symbol } })
    return {
      ...res,
      data: res.data || null
    }
  }
}

export const indicatorApi = {
  getParams: async (id) => {
    const res = await http.get('/api/indicator/getIndicatorParams', { params: { indicator_id: id } })
    return {
      ...res,
      data: Array.isArray(res.data) ? res.data : (res.data?.params || [])
    }
  },
  parseStrategyConfig: async (code) => {
    const res = await http.post('/api/indicator/parseStrategyConfig', { code: code || '' })
    return {
      ...res,
      data: res.data || { strategyConfig: {}, indicatorParams: [] }
    }
  }
}

export const userApi = {
  getProfile: () => http.get('/api/users/profile'),
  updateProfile: (data) => http.put('/api/users/profile/update', data),
  getNotificationSettings: () => http.get('/api/users/notification-settings'),
  updateNotificationSettings: (data) => http.put('/api/users/notification-settings', data),
  testNotificationSettings: () => http.post('/api/users/notification-settings/test'),
  changePassword: (data) => http.post('/api/users/change-password', data),
  getMyCreditsLog: async (params = {}) => {
    const res = await http.get('/api/users/my-credits-log', { params })
    const items = ensureArray(res.data?.items || res.data?.list)
    return {
      ...res,
      data: {
        list: items,
        items,
        total: Number(res.data?.total || 0),
        page: Number(res.data?.page || 1),
        page_size: Number(res.data?.page_size || 20),
        total_pages: Number(res.data?.total_pages || 0)
      }
    }
  },
  getMyReferrals: async (params = {}) => {
    const res = await http.get('/api/users/my-referrals', { params })
    return {
      ...res,
      data: {
        list: ensureArray(res.data?.list),
        total: Number(res.data?.total || 0),
        referral_code: res.data?.referral_code || '',
        referral_bonus: Number(res.data?.referral_bonus || 0),
        register_bonus: Number(res.data?.register_bonus || 0)
      }
    }
  }
}

export const globalMarketApi = {
  getOverview: async () => {
    const res = await http.get('/api/global-market/overview')
    return {
      ...res,
      data: res.data || { indices: [] }
    }
  },
  getCalendar: async (params = {}) => {
    const res = await http.get('/api/global-market/calendar', { params })
    const list = unwrapCalendarList(res.data)
      .map(normalizeCalendarEvent)
      .filter(Boolean)
    return {
      ...res,
      data: list
    }
  },
  getSentiment: async () => {
    const res = await http.get('/api/global-market/sentiment')
    const normalized = normalizeGlobalSentiment(res.data)
    return {
      ...res,
      data: normalized
    }
  }
}

export const billingApi = {
  /**
   * v3.0.6+ — list enabled USDT chains so the chain picker can render
   * before the order is created. Chains without a configured receiving
   * address are filtered out by the backend, so the response can be
   * rendered verbatim.
   */
  listUsdtChains: async () => {
    const res = await http.get('/api/billing/usdt/chains')
    return {
      ...res,
      data: res.data || { chains: [] }
    }
  },
  purchase: (plan) => http.post('/api/billing/purchase', { plan }),
  createUsdtOrder: (plan, chain) => {
    const payload = { plan }
    if (chain) payload.chain = chain
    return http.post('/api/billing/usdt/create', payload)
  },
  getUsdtOrder: (orderId, refresh = true) => http.get(`/api/billing/usdt/order/${orderId}`, {
    params: { refresh: refresh ? 1 : 0 }
  })
}

/** 会员权益/积分 API */
export const membershipApi = {
  getBenefits: async () => {
    const res = await http.get('/api/membership/benefits')
    return { ...res, data: Array.isArray(res?.benefits) ? res.benefits : [] }
  },
  getPackages: async () => {
    const res = await http.get('/api/membership/packages')
    return { ...res, data: Array.isArray(res?.packages) ? res.packages : [] }
  },
  getMyMembership: async () => {
    const res = await http.get('/api/membership/my')
    return { ...res, data: res?.data || res || {} }
  }
}

/** 积分支付 API */
export const paymentApi = {
  createCreditsPayment: (data) => http.post('/api/payment/credits', data),
  getPaymentById: (paymentId) => http.get(`/api/payment/${paymentId}`)
}

/** 订单查询 */
export const orderApi = {
  /** 按机器人ID和状态查询订单列表 */
  list: async ({ robotId, status, pageNum = 1, pageSize = 50 } = {}) => {
    const res = await http.get('/api/order/list', {
      params: { robotId, status, pageNum, pageSize }
    })
    return {
      ...res,
      data: ensureArray(res.data?.records || res.data?.list || res.data)
    }
  }
}

export default http
