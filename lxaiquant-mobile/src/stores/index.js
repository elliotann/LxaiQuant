import { createPinia, defineStore } from 'pinia'
import { DEFAULT_SERVER_URL, DEFAULT_THEME } from '@/config'
import { initialLocale, setLocale as applyLocale } from '@/locales'

export const pinia = createPinia()

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    userInfo: null,
    isLoggedIn: !!localStorage.getItem('token')
  }),

  actions: {
    setToken(token) {
      this.token = token
      this.isLoggedIn = !!token
      if (token) {
        localStorage.setItem('token', token)
      } else {
        localStorage.removeItem('token')
      }
    },

    setUserInfo(info) {
      this.userInfo = info
    },

    logout() {
      this.token = ''
      this.userInfo = null
      this.isLoggedIn = false
      localStorage.removeItem('token')
    }
  }
})

export const useTradingBotStore = defineStore('tradingBot', {
  state: () => ({
    bots: [],
    loading: false
  }),

  getters: {
    statusCounts: (state) => {
      const counts = { running: 0, stopped: 0, error: 0, total: state.bots.length }
      state.bots.forEach((item) => {
        if (item.status === 'running') counts.running++
        else if (item.status === 'stopped') counts.stopped++
        else if (item.status === 'error') counts.error++
      })
      return counts
    },
    runningBots: (state) => state.bots.filter((item) => item.status === 'running'),
    alertBots: (state) => state.bots.filter((item) => item.status === 'error'),
    stoppedBots: (state) => state.bots.filter((item) => item.status === 'stopped')
  },

  actions: {
    setBots(list) {
      this.bots = Array.isArray(list) ? list : []
    },

    updateBot(id, patch) {
      const target = this.bots.find((item) => item.id === id)
      if (target) {
        Object.assign(target, patch)
      }
    },

    setLoading(val) {
      this.loading = val
    }
  }
})

export const useCredentialsStore = defineStore('credentials', {
  state: () => ({
    items: [],
    egressIp: null,
    loading: false
  }),

  getters: {
    hasCredentials: (state) => state.items.length > 0,
    cryptoItems: (state) => state.items.filter((item) => !['ibkr', 'mt5'].includes(item.exchange_id))
  },

  actions: {
    setItems(list) {
      this.items = Array.isArray(list) ? list : []
    },

    setEgressIp(data) {
      this.egressIp = data || null
    },

    setLoading(val) {
      this.loading = val
    }
  }
})

export const useDashboardStore = defineStore('dashboard', {
  state: () => ({
    summary: null,
    loading: false
  }),

  getters: {
    totalAssets: (state) => Number(state.summary?.totalAssets || 0),
    totalPnl: (state) => Number(state.summary?.totalPnL || 0),
    realizedPnl: (state) => 0,
    unrealizedPnl: (state) => 0,
    positions: (state) => [],
    recentTrades: (state) => [],
    performance: (state) => ({}),
    winRate: (state) => 0,
    totalTrades: (state) => 0,
    profitFactor: (state) => 0,
    maxDrawdownPct: (state) => 0,
    dailyPnlChart: (state) => [],
    todayPnl: (state) => Number(state.summary?.dailyPnL || 0),
    aiStrategyCount: (state) => 0,
    indicatorStrategyCount: (state) => 0
  },

  actions: {
    setSummary(data) {
      this.summary = data
    },

    setLoading(val) {
      this.loading = val
    }
  }
})

export const useSettingsStore = defineStore('settings', {
  state: () => ({
    serverUrl: localStorage.getItem('serverUrl') || DEFAULT_SERVER_URL,
    theme: localStorage.getItem('theme') || DEFAULT_THEME,
    locale: initialLocale
  }),

  actions: {
    setServerUrl(url) {
      this.serverUrl = url
      if (url) {
        localStorage.setItem('serverUrl', url)
      } else {
        localStorage.removeItem('serverUrl')
      }
    },

    setTheme(theme) {
      this.theme = theme
      localStorage.setItem('theme', theme)
      document.documentElement.setAttribute('data-theme', theme)
    },

    setLocale(locale) {
      this.locale = locale
      applyLocale(locale)
    }
  }
})

export const useAiAnalysisStore = defineStore('aiAnalysis', {
  state: () => ({
    history: [],
    total: 0,
    loading: false,
    lastResult: null
  }),

  actions: {
    setHistory(payload) {
      this.history = Array.isArray(payload?.list) ? payload.list : []
      this.total = Number(payload?.total || 0)
    },
    setLastResult(result) {
      this.lastResult = result || null
    },
    setLoading(val) {
      this.loading = val
    }
  }
})

export const useMarketStore = defineStore('market', {
  state: () => ({
    items: [],
    total: 0,
    loading: false,
    purchases: []
  }),

  actions: {
    setItems(list, total = 0) {
      this.items = Array.isArray(list) ? list : []
      this.total = Number(total || this.items.length)
    },
    setPurchases(list) {
      this.purchases = Array.isArray(list) ? list : []
    },
    setLoading(val) {
      this.loading = val
    }
  }
})

export const useNotificationStore = defineStore('notification', {
  state: () => ({
    notifications: [],
    unreadCount: 0
  }),

  actions: {
    setNotifications(list) {
      this.notifications = Array.isArray(list) ? list : []
      this.unreadCount = this.notifications.filter((item) => !item.is_read && !item.read).length
    },

    setUnreadCount(count) {
      this.unreadCount = Number(count || 0)
    },

    markAsRead(id) {
      const notification = this.notifications.find((item) => item.id === id)
      if (notification && !notification.is_read && !notification.read) {
        notification.is_read = 1
        notification.read = true
        this.unreadCount = Math.max(0, this.unreadCount - 1)
      }
    },

    markAllAsRead() {
      this.notifications.forEach((item) => {
        item.is_read = 1
        item.read = true
      })
      this.unreadCount = 0
    }
  }
})

export const useWatchlistStore = defineStore('watchlist', {
  state: () => ({
    items: [],
    activeSymbol: localStorage.getItem('watchlist_active_symbol') || '',
    activeMarket: localStorage.getItem('watchlist_active_market') || 'Crypto',
    loading: false
  }),

  getters: {
    cryptoItems: (state) => state.items.filter((i) => (i.market || '').toLowerCase() === 'crypto'),
    activeItem: (state) => state.items.find((i) => i.symbol === state.activeSymbol) || null
  },

  actions: {
    setItems(list) {
      this.items = Array.isArray(list) ? list : []
      if (!this.activeSymbol && this.items.length > 0) {
        const first = this.items.find((i) => (i.market || '').toLowerCase() === 'crypto') || this.items[0]
        if (first) {
          this.activeSymbol = first.symbol
          this.activeMarket = first.market || 'Crypto'
          localStorage.setItem('watchlist_active_symbol', this.activeSymbol)
          localStorage.setItem('watchlist_active_market', this.activeMarket)
        }
      }
    },

    setActive(symbol, market) {
      this.activeSymbol = symbol || ''
      if (market) this.activeMarket = market
      if (symbol) localStorage.setItem('watchlist_active_symbol', symbol)
      else localStorage.removeItem('watchlist_active_symbol')
      if (market) localStorage.setItem('watchlist_active_market', market)
    },

    setLoading(val) {
      this.loading = val
    }
  }
})

export const useQuickTradeStore = defineStore('quickTrade', {
  state: () => ({
    selectedCredentialId: null,
    marketType: 'spot',
    balance: null,
    positions: [],
    history: [],
    loading: false
  }),

  actions: {
    setSelectedCredential(id) {
      this.selectedCredentialId = id || null
    },

    setMarketType(type) {
      this.marketType = type || 'spot'
    },

    setBalance(data) {
      this.balance = data || null
    },

    setPositions(list) {
      this.positions = Array.isArray(list) ? list : []
    },

    setHistory(list) {
      this.history = Array.isArray(list) ? list : []
    },

    setLoading(val) {
      this.loading = val
    }
  }
})

export default pinia
